/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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

import org.apache.ibatis.javassist.tools.rmi.ObjectImporter;
import org.apache.ibatis.javassist.tools.rmi.Proxy;
import org.apache.ibatis.javassist.tools.rmi.RemoteRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ObjectImporterTest {
    @Test
    @Timeout(60)
    void callsARemoteServiceUsingACompiledClientStub() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(10000);
            Future<?> server = executor.submit(() -> {
                for (int request = 0; request < 3; request++) {
                    try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(10000);
                        reply(socket, request);
                    }
                }
                return null;
            });

            ObjectImporter importer = new ObjectImporter(listener.getInetAddress().getHostAddress(),
                    listener.getLocalPort());
            RemoteGreeting remote = assertInstanceOf(RemoteGreeting.class, importer.lookupObject("greeting"));
            assertEquals(7, remote._getObjectId());
            assertEquals("Hello, Ada", remote.greet("Ada", remote));
            assertEquals(7, remote.copy()._getObjectId());
            server.get(30, TimeUnit.SECONDS);
        }
    }

    private static void reply(Socket socket, int request) throws Exception {
        InputStream raw = new BufferedInputStream(socket.getInputStream());
        String header = readHeader(raw);
        ObjectInputStream input = new ObjectInputStream(raw);
        socket.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
        if (request == 0) {
            assertEquals("POST /lookup HTTP/1.0\r\n\r\n", header);
            assertEquals("greeting", input.readUTF());
            output.writeInt(7);
            output.writeUTF(RemoteGreeting.class.getName());
        } else {
            assertEquals("POST /rmi HTTP/1.0\r\n\r\n", header);
            assertEquals(7, input.readInt());
            assertEquals(request - 1, input.readInt());
            output.writeBoolean(true);
            if (request == 1) {
                assertEquals(2, input.readInt());
                assertEquals("Ada", input.readObject());
                RemoteRef reference = assertInstanceOf(RemoteRef.class, input.readObject());
                assertEquals(7, reference.oid);
                output.writeObject("Hello, Ada");
            } else {
                assertEquals(0, input.readInt());
                output.writeObject(new RemoteRef(7, RemoteGreeting.class.getName()));
            }
        }
        output.flush();
    }

    private static String readHeader(InputStream input) throws IOException {
        StringBuilder header = new StringBuilder();
        while (header.length() < 1024) {
            int next = input.read();
            if (next == -1) {
                throw new IOException("Incomplete RMI request header");
            }
            header.append((char) next);
            if (header.toString().endsWith("\r\n\r\n")) {
                return header.toString();
            }
        }
        throw new IOException("RMI request header exceeds the size limit");
    }

    // A statically compiled RMI client stub needs no runtime class definition.
    public static class RemoteGreeting implements Proxy {
        private final ObjectImporter importer;
        private final int objectId;

        public RemoteGreeting(ObjectImporter importer, int objectId) {
            this.importer = importer;
            this.objectId = objectId;
        }

        @Override
        public int _getObjectId() {
            return objectId;
        }

        public String greet(String name, RemoteGreeting other) {
            return (String) importer.call(objectId, 0, new Object[]{name, other});
        }

        public RemoteGreeting copy() {
            return (RemoteGreeting) importer.call(objectId, 1, new Object[0]);
        }
    }
}
