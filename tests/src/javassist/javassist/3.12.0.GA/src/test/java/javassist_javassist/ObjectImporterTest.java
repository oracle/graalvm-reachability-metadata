/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import javassist.tools.rmi.ObjectImporter;
import javassist.tools.rmi.Proxy;
import javassist.tools.rmi.RemoteRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectImporterTest {
    @Test
    void callSerializesProxyAndValueArgumentsThenCreatesReturnedProxy() throws Exception {
        RemoteRef response = new RemoteRef(90, RemoteProxyFixture.class.getName());
        try (RmiTestServer server = RmiTestServer.start(response)) {
            ObjectImporter importer = new ObjectImporter(server.host(), server.port());
            RemoteProxyFixture proxyArgument = new RemoteProxyFixture(importer, 44);

            Object result = importer.call(7, 11, new Object[] { proxyArgument, "plain-value" });

            assertThat(server.requestLine()).isEqualTo("POST /rmi HTTP/1.0");
            assertThat(server.objectId()).isEqualTo(7);
            assertThat(server.methodId()).isEqualTo(11);
            assertThat(server.parameters()).hasSize(2);
            assertThat(server.parameters()[0]).isInstanceOf(RemoteRef.class);
            RemoteRef serializedProxyReference = (RemoteRef) server.parameters()[0];
            assertThat(serializedProxyReference.oid).isEqualTo(44);
            assertThat(serializedProxyReference.classname).isNull();
            assertThat(server.parameters()[1]).isEqualTo("plain-value");

            assertThat(result).isInstanceOf(RemoteProxyFixture.class);
            RemoteProxyFixture returnedProxy = (RemoteProxyFixture) result;
            assertThat(returnedProxy.importer()).isSameAs(importer);
            assertThat(returnedProxy._getObjectId()).isEqualTo(90);
        }
    }

    public static class RemoteProxyFixture implements Proxy, Serializable {
        private static final long serialVersionUID = 1L;

        private final ObjectImporter importer;
        private final int objectId;

        public RemoteProxyFixture(ObjectImporter importer, int objectId) {
            this.importer = importer;
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }

        ObjectImporter importer() {
            return importer;
        }
    }

    private static final class RmiTestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final RemoteRef response;
        private final Thread thread;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private volatile String requestLine;
        private volatile int objectId;
        private volatile int methodId;
        private volatile Object[] parameters;

        private RmiTestServer(ServerSocket serverSocket, RemoteRef response) {
            this.serverSocket = serverSocket;
            this.response = response;
            this.thread = new Thread(this::serve, "object-importer-test-server");
            this.thread.setDaemon(true);
        }

        static RmiTestServer start(RemoteRef response) throws IOException {
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            ServerSocket serverSocket = new ServerSocket(0, 1, loopback);
            RmiTestServer server = new RmiTestServer(serverSocket, response);
            server.thread.start();
            return server;
        }

        String host() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String requestLine() {
            return requestLine;
        }

        int objectId() {
            return objectId;
        }

        int methodId() {
            return methodId;
        }

        Object[] parameters() {
            return parameters;
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                InputStream input = socket.getInputStream();
                requestLine = readHttpRequest(input);
                ObjectInputStream objectInput = new ObjectInputStream(input);
                objectId = objectInput.readInt();
                methodId = objectInput.readInt();
                int parameterCount = objectInput.readInt();
                Object[] readParameters = new Object[parameterCount];
                for (int i = 0; i < parameterCount; i++) {
                    readParameters[i] = objectInput.readObject();
                }
                parameters = readParameters;

                OutputStream output = socket.getOutputStream();
                output.write("HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                    objectOutput.writeBoolean(true);
                    objectOutput.writeObject(response);
                    objectOutput.flush();
                }
            } catch (Throwable e) {
                failure.set(e);
            }
        }

        private static String readHttpRequest(InputStream input) throws IOException {
            String firstLine = readLine(input);
            String line;
            do {
                line = readLine(input);
            } while (!line.isEmpty());
            return firstLine;
        }

        private static String readLine(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int value = input.read();
                if (value < 0) {
                    throw new IOException("Unexpected end of HTTP request");
                }
                if (value == '\r') {
                    int lineFeed = input.read();
                    if (lineFeed != '\n') {
                        throw new IOException("Malformed HTTP line ending");
                    }
                    return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
                }
                bytes.write(value);
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(5_000L);
            if (thread.isAlive()) {
                throw new AssertionError("RMI test server did not finish");
            }
            Throwable thrown = failure.get();
            if (thrown != null) {
                throw new AssertionError("RMI test server failed", thrown);
            }
        }
    }
}
