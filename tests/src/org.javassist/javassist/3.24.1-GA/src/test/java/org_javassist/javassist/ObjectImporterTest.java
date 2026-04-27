/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javassist.tools.rmi.ObjectImporter;
import javassist.tools.rmi.Proxy;
import javassist.tools.rmi.RemoteRef;

import org.junit.jupiter.api.Test;

public class ObjectImporterTest {
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final byte[] HTTP_OK = "HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void lookupObjectInstantiatesProxyReturnedByServer() throws Exception {
        LookupExchange exchange = new LookupExchange(37, RemoteGreetingProxy.class.getName());
        try (SingleRequestServer server = SingleRequestServer.start(exchange)) {
            ObjectImporter importer = new ObjectImporter("127.0.0.1", server.port());

            Object proxy = importer.lookupObject("greeting");

            assertThat(proxy).isInstanceOf(RemoteGreetingProxy.class);
            assertThat(((RemoteGreetingProxy) proxy)._getObjectId()).isEqualTo(37);
            server.awaitRequest();
            assertThat(exchange.lookupName()).isEqualTo("greeting");
        }
    }

    @Test
    void proxyCallSerializesRemoteReferencesAndRegularParametersThenReadsResponse() throws Exception {
        CallExchange exchange = new CallExchange("hello from server");
        try (SingleRequestServer server = SingleRequestServer.start(exchange)) {
            ObjectImporter importer = new ObjectImporter("127.0.0.1", server.port());
            RemoteGreetingProxy proxy = new RemoteGreetingProxy(importer, 51);

            Object result = proxy.greetWithRemotePeer("Ada", proxy);

            assertThat(result).isEqualTo("hello from server");
            server.awaitRequest();
            CapturedInvocation invocation = exchange.invocation();
            assertThat(invocation.objectId()).isEqualTo(51);
            assertThat(invocation.methodId()).isEqualTo(RemoteGreetingProxy.GREET_WITH_PEER_METHOD_ID);
            assertThat(invocation.parameters()).hasSize(2);
            assertThat(invocation.parameters().get(0)).isEqualTo("Ada");
            assertThat(invocation.parameters().get(1)).isInstanceOf(RemoteRef.class);
            RemoteRef peerReference = (RemoteRef) invocation.parameters().get(1);
            assertThat(peerReference.oid).isEqualTo(51);
            assertThat(peerReference.classname).isNull();
        }
    }

    public static class RemoteGreetingProxy implements Proxy {
        public static final int GREET_WITH_PEER_METHOD_ID = 7;

        private final ObjectImporter importer;
        private final int objectId;

        public RemoteGreetingProxy(ObjectImporter importer, int objectId) {
            this.importer = importer;
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }

        public Object greetWithRemotePeer(String name, Proxy peer) {
            return importer.call(objectId, GREET_WITH_PEER_METHOD_ID, new Object[] {name, peer});
        }
    }

    private interface ServerExchange {
        void handle(Socket socket) throws Exception;
    }

    private static final class SingleRequestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final CompletableFuture<Void> request;

        private SingleRequestServer(ServerExchange exchange) throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            request = CompletableFuture.runAsync(() -> accept(exchange));
        }

        static SingleRequestServer start(ServerExchange exchange) throws IOException {
            return new SingleRequestServer(exchange);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void awaitRequest() throws InterruptedException, ExecutionException, TimeoutException {
            request.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

        private void accept(ServerExchange exchange) {
            try (Socket socket = serverSocket.accept()) {
                exchange.handle(socket);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class LookupExchange implements ServerExchange {
        private final int objectId;
        private final String proxyClassName;
        private String lookupName;

        private LookupExchange(int objectId, String proxyClassName) {
            this.objectId = objectId;
            this.proxyClassName = proxyClassName;
        }

        String lookupName() {
            return lookupName;
        }

        @Override
        public void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            assertThat(readHttpRequestLine(input)).isEqualTo("POST /lookup HTTP/1.0");
            ObjectInputStream objectInput = new ObjectInputStream(input);
            lookupName = objectInput.readUTF();

            OutputStream output = socket.getOutputStream();
            output.write(HTTP_OK);
            try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeInt(objectId);
                objectOutput.writeUTF(proxyClassName);
            }
        }
    }

    private static final class CallExchange implements ServerExchange {
        private final Object response;
        private CapturedInvocation invocation;

        private CallExchange(Object response) {
            this.response = response;
        }

        CapturedInvocation invocation() {
            return invocation;
        }

        @Override
        public void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            assertThat(readHttpRequestLine(input)).isEqualTo("POST /rmi HTTP/1.0");
            ObjectInputStream objectInput = new ObjectInputStream(input);
            int objectId = objectInput.readInt();
            int methodId = objectInput.readInt();
            int parameterCount = objectInput.readInt();
            List<Object> parameters = new ArrayList<>();
            for (int i = 0; i < parameterCount; i++) {
                parameters.add(objectInput.readObject());
            }
            invocation = new CapturedInvocation(objectId, methodId, parameters);

            OutputStream output = socket.getOutputStream();
            output.write(HTTP_OK);
            try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeBoolean(true);
                objectOutput.writeObject(response);
            }
        }
    }

    private static final class CapturedInvocation {
        private final int objectId;
        private final int methodId;
        private final List<Object> parameters;

        private CapturedInvocation(int objectId, int methodId, List<Object> parameters) {
            this.objectId = objectId;
            this.methodId = methodId;
            this.parameters = parameters;
        }

        int objectId() {
            return objectId;
        }

        int methodId() {
            return methodId;
        }

        List<Object> parameters() {
            return parameters;
        }
    }

    private static String readHttpRequestLine(InputStream input) throws IOException {
        String requestLine = readAsciiLine(input);
        String headerLine;
        do {
            headerLine = readAsciiLine(input);
        } while (!headerLine.isEmpty());
        return requestLine;
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int current;
        while ((current = input.read()) >= 0) {
            if (current == '\r') {
                int next = input.read();
                if (next != '\n') {
                    throw new IOException("Expected LF after CR in HTTP header");
                }
                return line.toString();
            }
            line.append((char) current);
        }
        throw new IOException("Unexpected end of stream while reading HTTP header");
    }
}
