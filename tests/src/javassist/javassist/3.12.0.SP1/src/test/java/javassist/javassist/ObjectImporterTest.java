/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javassist.tools.rmi.ObjectImporter;
import javassist.tools.rmi.Proxy;
import javassist.tools.rmi.RemoteRef;

import org.junit.jupiter.api.Test;

public class ObjectImporterTest {
    @Test
    void lookupObjectCreatesProxyFromServerDescriptor() throws Exception {
        try (OneShotRmiServer server = OneShotRmiServer.start(socket -> {
            InputStream input = socket.getInputStream();
            skipHttpHeader(input);
            ObjectInputStream objectInput = new ObjectInputStream(input);
            assertThat(objectInput.readUTF()).isEqualTo("catalog");

            OutputStream output = socket.getOutputStream();
            writeHttpOk(output);
            ObjectOutputStream objectOutput = new ObjectOutputStream(output);
            objectOutput.writeInt(41);
            objectOutput.writeUTF(ImportedProxy.class.getName());
            objectOutput.flush();
        })) {
            ObjectImporter importer = new ObjectImporter("127.0.0.1", server.port());

            Object imported = importer.lookupObject("catalog");

            assertThat(imported).isInstanceOf(ImportedProxy.class);
            assertThat(((ImportedProxy) imported)._getObjectId()).isEqualTo(41);
            server.awaitHandled();
        }
    }

    @Test
    void callSerializesRemoteReferencesAndValueParameters() throws Exception {
        try (OneShotRmiServer server = OneShotRmiServer.start(socket -> {
            InputStream input = socket.getInputStream();
            skipHttpHeader(input);
            ObjectInputStream objectInput = new ObjectInputStream(input);
            assertThat(objectInput.readInt()).isEqualTo(7);
            assertThat(objectInput.readInt()).isEqualTo(13);
            assertThat(objectInput.readInt()).isEqualTo(2);

            Object firstParameter = objectInput.readObject();
            Object secondParameter = objectInput.readObject();
            assertThat(firstParameter).isInstanceOf(RemoteRef.class);
            assertThat(((RemoteRef) firstParameter).oid).isEqualTo(123);
            assertThat(secondParameter).isEqualTo("payload");

            OutputStream output = socket.getOutputStream();
            writeHttpOk(output);
            ObjectOutputStream objectOutput = new ObjectOutputStream(output);
            objectOutput.writeBoolean(true);
            objectOutput.writeObject("accepted");
            objectOutput.flush();
        })) {
            ObjectImporter importer = new ObjectImporter("127.0.0.1", server.port());
            Object proxyArgument = new ImportedProxy(importer, 123);

            Object result = importer.call(7, 13, new Object[] {proxyArgument, "payload"});

            assertThat(result).isEqualTo("accepted");
            server.awaitHandled();
        }
    }

    private static void skipHttpHeader(InputStream input) throws IOException {
        while (!readAsciiLine(input).isEmpty()) {
            // Continue until the empty line separating the HTTP header and body.
        }
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) >= 0) {
            if (current == '\r') {
                int next = input.read();
                if (next != '\n') {
                    throw new IOException("expected LF after CR");
                }

                return line.toString(StandardCharsets.US_ASCII.name());
            }

            line.write(current);
        }

        throw new IOException("unexpected end of HTTP header");
    }

    private static void writeHttpOk(OutputStream output) throws IOException {
        output.write("HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    public static final class ImportedProxy implements Proxy, Serializable {
        private final ObjectImporter importer;
        private final int objectId;

        public ImportedProxy(ObjectImporter importer, int objectId) {
            this.importer = importer;
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }

        public ObjectImporter importer() {
            return importer;
        }
    }

    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }

    private static final class OneShotRmiServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Future<?> future;

        private OneShotRmiServer(ServerSocket serverSocket, SocketHandler handler) {
            this.serverSocket = serverSocket;
            this.executor = Executors.newSingleThreadExecutor();
            this.future = executor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    handler.handle(socket);
                }

                return null;
            });
        }

        static OneShotRmiServer start(SocketHandler handler) throws IOException {
            ServerSocket serverSocket = new ServerSocket(0);
            return new OneShotRmiServer(serverSocket, handler);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void awaitHandled() throws Exception {
            future.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            executor.shutdownNow();
        }
    }
}
