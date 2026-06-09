/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.tools.rmi.AppletServer;
import javassist.tools.rmi.RemoteRef;

import org.junit.jupiter.api.Test;

public class AppletServerTest {
    @Test
    void dispatchesRmiRequestToExportedObjectAndSerializesResult() throws Exception {
        initializeDefaultClassPool();
        AppletServer server = new AppletServer(0);
        try {
            RemoteRef remoteRef = new RemoteRef(10, RemoteRef.class.getName());
            int objectId = server.exportObject("remoteRef", remoteRef);
            int methodId = methodId(RemoteRef.class, "equals", Object.class);
            byte[] requestBody = rmiRequestBody(objectId, methodId, "not-the-same-object");
            ByteArrayOutputStream response = new ByteArrayOutputStream();

            server.doReply(
                    new ByteArrayInputStream(requestBody),
                    response,
                    "POST /rmi HTTP/1.0");

            ObjectInputStream objectInput = rmiResponseBody(response.toByteArray());
            assertThat(objectInput.readBoolean()).isTrue();
            assertThat(objectInput.readObject()).isEqualTo(Boolean.FALSE);
            objectInput.close();
        } finally {
            server.end();
        }
    }

    private static void initializeDefaultClassPool() {
        ClassPool.getDefault().insertClassPath(new ClassClassPath(RemoteRef.class));
    }

    private static int methodId(Class<?> serviceClass, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method expected = serviceClass.getMethod(name, parameterTypes);
        Method[] methods = serviceClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].equals(expected)) {
                return i;
            }
        }

        throw new NoSuchMethodException(expected.toString());
    }

    private static byte[] rmiRequestBody(int objectId, int methodId, Object... parameters)
            throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ObjectOutputStream objectOutput = new ObjectOutputStream(body);
        objectOutput.writeInt(objectId);
        objectOutput.writeInt(methodId);
        objectOutput.writeInt(parameters.length);
        for (Object parameter : parameters) {
            objectOutput.writeObject(parameter);
        }

        objectOutput.flush();
        return body.toByteArray();
    }

    private static ObjectInputStream rmiResponseBody(byte[] response) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(response);
        while (!readAsciiLine(input).isEmpty()) {
            // Continue until the empty line separating the HTTP header and body.
        }

        return new ObjectInputStream(input);
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

        throw new IOException("unexpected end of HTTP response");
    }
}
