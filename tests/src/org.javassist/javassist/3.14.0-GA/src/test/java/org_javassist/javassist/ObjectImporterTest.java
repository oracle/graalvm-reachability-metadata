/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
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
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectImporterTest {
    @Test
    @Timeout(90)
    void importsAStaticClientStubAndExchangesValuesAndRemoteReferences() throws Exception {
        try (RmiPeer peer = new RmiPeer()) {
            ObjectImporter importer = new ObjectImporter("127.0.0.1", peer.port());
            RemoteCatalog catalog = assertInstanceOf(RemoteCatalog.class, importer.lookupObject("catalog"));
            assertEquals(41, catalog._getObjectId());
            assertSame(importer, catalog.importer);
            assertEquals("accepted:payload", catalog.echo("payload", catalog));
            RemoteCatalog related = catalog.related();
            assertEquals(73, related._getObjectId());
            assertSame(importer, related.importer);
            peer.await();
        }
    }

    // A statically compiled client implementing the documented RMI stub protocol.
    public static class RemoteCatalog implements Proxy {
        private final ObjectImporter importer;
        private final int objectId;

        public RemoteCatalog(ObjectImporter importer, int objectId) {
            this.importer = importer;
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }

        public String echo(String value, RemoteCatalog reference) {
            return (String) importer.call(objectId, 0, new Object[]{value, reference});
        }

        public RemoteCatalog related() {
            return (RemoteCatalog) importer.call(objectId, 1, new Object[0]);
        }
    }

    private static class RmiPeer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor;
        private final Future<?> requests;

        RmiPeer() throws Exception {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout(30000);
            executor = Executors.newSingleThreadExecutor();
            requests = executor.submit(() -> {
                for (int request = 0; request < 3; request++) {
                    try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(30000);
                        reply(socket, request);
                    }
                }
                return null;
            });
        }

        int port() {
            return listener.getLocalPort();
        }

        void await() throws Exception {
            requests.get(30, TimeUnit.SECONDS);
        }

        private void reply(Socket socket, int request) throws Exception {
            String command = request == 0 ? "lookup" : "rmi";
            byte[] header = ("POST /" + command + " HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            assertArrayEquals(header, socket.getInputStream().readNBytes(header.length));
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
            if (request == 0) {
                assertEquals("catalog", input.readUTF());
            } else {
                assertEquals(41, input.readInt());
                assertEquals(request - 1, input.readInt());
                assertEquals(request == 1 ? 2 : 0, input.readInt());
                if (request == 1) {
                    assertEquals("payload", input.readObject());
                    RemoteRef reference = assertInstanceOf(RemoteRef.class, input.readObject());
                    assertEquals(41, reference.oid);
                }
            }
            socket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            if (request == 0) {
                output.writeInt(41);
                output.writeUTF(RemoteCatalog.class.getName());
            } else {
                output.writeBoolean(true);
                output.writeObject(request == 1 ? "accepted:payload" : new RemoteRef(73, RemoteCatalog.class.getName()));
            }
            output.flush();
        }

        @Override
        public void close() throws Exception {
            listener.close();
            requests.cancel(true);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }
}
