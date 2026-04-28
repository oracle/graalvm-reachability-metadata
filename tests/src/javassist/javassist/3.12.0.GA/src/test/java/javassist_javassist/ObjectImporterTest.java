/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javassist.tools.rmi.ObjectImporter;
import javassist.tools.rmi.ObjectImporterClassAccess;
import javassist.tools.rmi.Proxy;
import javassist.tools.rmi.RemoteRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectImporterTest {
    @Test
    void generatedClassLiteralAccessorLoadsImporterClass() {
        assertThat(ObjectImporterClassAccess.loadImporterClassByGeneratedAccessor()).isEqualTo(ObjectImporter.class);
    }

    @Test
    void lookupObjectCreatesProxyFromServerResponse() throws Exception {
        try (SingleRequestServer server = new SingleRequestServer(socket -> {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            skipHttpHeader(input);
            ObjectInputStream objectInput = new ObjectInputStream(input);
            assertThat(objectInput.readUTF()).isEqualTo("sample-service");

            ObjectOutputStream objectOutput = writeHttpObjectResponse(socket);
            objectOutput.writeInt(42);
            objectOutput.writeUTF(RemoteProxyFixture.class.getName());
            objectOutput.flush();
        })) {
            ObjectImporter importer = new ObjectImporter(server.host(), server.port());

            Object imported = importer.lookupObject("sample-service");

            assertThat(imported).isInstanceOf(RemoteProxyFixture.class);
            RemoteProxyFixture proxy = (RemoteProxyFixture) imported;
            assertThat(proxy.importer()).isSameAs(importer);
            assertThat(proxy._getObjectId()).isEqualTo(42);
        }
    }

    @Test
    void callSerializesPlainAndProxyParametersAndCreatesProxyFromRemoteReference() throws Exception {
        try (SingleRequestServer server = new SingleRequestServer(socket -> {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            skipHttpHeader(input);
            ObjectInputStream objectInput = new ObjectInputStream(input);
            assertThat(objectInput.readInt()).isEqualTo(7);
            assertThat(objectInput.readInt()).isEqualTo(9);
            assertThat(objectInput.readInt()).isEqualTo(2);
            assertThat(objectInput.readObject()).isEqualTo("payload");
            RemoteRef proxyReference = (RemoteRef) objectInput.readObject();
            assertThat(proxyReference.oid).isEqualTo(123);

            ObjectOutputStream objectOutput = writeHttpObjectResponse(socket);
            objectOutput.writeBoolean(true);
            objectOutput.writeObject(new RemoteRef(456, RemoteProxyFixture.class.getName()));
            objectOutput.flush();
        })) {
            ObjectImporter importer = new ObjectImporter(server.host(), server.port());
            RemoteProxyFixture argumentProxy = new RemoteProxyFixture(importer, 123);

            Object result = importer.call(7, 9, new Object[] {"payload", argumentProxy});

            assertThat(result).isInstanceOf(RemoteProxyFixture.class);
            RemoteProxyFixture resultProxy = (RemoteProxyFixture) result;
            assertThat(resultProxy.importer()).isSameAs(importer);
            assertThat(resultProxy._getObjectId()).isEqualTo(456);
        }
    }

    private static ObjectOutputStream writeHttpObjectResponse(Socket socket) throws IOException {
        BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
        output.write("HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        output.flush();
        return new ObjectOutputStream(output);
    }

    private static void skipHttpHeader(InputStream input) throws IOException {
        byte[] marker = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        int matched = 0;
        int current;
        while ((current = input.read()) >= 0) {
            if (current == marker[matched]) {
                matched++;
                if (matched == marker.length) {
                    return;
                }
            } else {
                matched = current == marker[0] ? 1 : 0;
            }
        }

        throw new IOException("HTTP request header did not terminate");
    }

    public static class RemoteProxyFixture implements Proxy {
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

    private interface SocketExchange {
        void exchange(Socket socket) throws Exception;
    }

    private static final class SingleRequestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private volatile Throwable failure;

        private SingleRequestServer(SocketExchange exchange) throws IOException {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            serverSocket = new ServerSocket(0, 1, loopback);
            thread = new Thread(() -> serve(exchange), "object-importer-test-server");
            thread.start();
        }

        private String host() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void serve(SocketExchange exchange) {
            try (Socket socket = serverSocket.accept()) {
                exchange.exchange(socket);
            } catch (Throwable throwable) {
                failure = throwable;
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(5000L);
            if (thread.isAlive()) {
                throw new AssertionError("Test server did not stop after handling a request");
            }

            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
        }
    }
}
