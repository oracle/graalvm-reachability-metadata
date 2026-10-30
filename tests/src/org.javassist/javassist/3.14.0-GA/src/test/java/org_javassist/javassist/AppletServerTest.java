/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.tools.rmi.AppletServer;
import javassist.tools.rmi.RemoteRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppletServerTest {
    @Test
    void exportsLooksUpAndInvokesAnExistingServiceWithValuesAndReferences() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        ClassPath libraryPath = pool.insertClassPath(new ClassClassPath(AppletServer.class));
        ClassPath servicePath = pool.insertClassPath(new ClassClassPath(RmiCatalog.class));
        AppletServer server = new AppletServer(0);
        try {
            int identifier = server.exportObject("catalog", new RmiCatalog());
            ByteArrayOutputStream lookup = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(lookup)) {
                output.writeUTF("catalog");
            }
            try (ObjectInputStream input = reply(server, "lookup", lookup.toByteArray())) {
                assertEquals(identifier, input.readInt());
                assertEquals(RmiCatalog.class.getName(), input.readUTF());
            }
            assertEquals("invoice", invoke(server, identifier, "echo", "invoice"));
            assertEquals(Boolean.TRUE, invoke(server, identifier, "sameCatalog", new RemoteRef(identifier)));
        } finally {
            server.end();
            pool.get(RmiCatalog.class.getName()).detach();
            pool.removeClassPath(servicePath);
            pool.removeClassPath(libraryPath);
        }
    }

    private static Object invoke(AppletServer server, int identifier, String methodName, Object argument) throws Exception {
        // The RMI wire protocol indexes methods in the exported class's public method array.
        Method[] methods = RmiCatalog.class.getMethods();
        int methodId = -1;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(methodName)) {
                methodId = i;
                break;
            }
        }
        assertTrue(methodId >= 0);
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(request)) {
            output.writeInt(identifier);
            output.writeInt(methodId);
            output.writeInt(1);
            output.writeObject(argument);
        }
        try (ObjectInputStream input = reply(server, "rmi", request.toByteArray())) {
            assertTrue(input.readBoolean());
            return input.readObject();
        }
    }

    private static ObjectInputStream reply(AppletServer server, String endpoint, byte[] request) throws Exception {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        server.doReply(new ByteArrayInputStream(request), response, "POST /" + endpoint + " HTTP/1.0");
        ByteArrayInputStream bytes = new ByteArrayInputStream(response.toByteArray());
        byte[] header = "HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        assertEquals(new String(header, StandardCharsets.US_ASCII),
                new String(bytes.readNBytes(header.length), StandardCharsets.US_ASCII));
        return new ObjectInputStream(bytes);
    }
}
