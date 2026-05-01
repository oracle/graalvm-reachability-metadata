/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.tools.rmi.AppletServer;
import javassist.tools.rmi.RemoteRef;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AppletServerTest {
    @Test
    void exportedObjectReceivesRemoteInvocationWithSerializedParameters() throws Exception {
        AppletServer server = null;
        try {
            addTestAndJavassistClassesToDefaultClassPool();
            server = new AppletServer(0);
            RemoteService remoteService = new RemoteService(10);
            Collaborator collaborator = new Collaborator(2);

            int remoteObjectId = server.exportObject("remote-service", remoteService);
            int collaboratorObjectId = server.exportObject("collaborator", collaborator);
            int methodId = methodId(RemoteService.class, "combineWith", Collaborator.class, int.class);

            ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
            server.doReply(
                    rmiRequest(remoteObjectId, methodId, new RemoteRef(collaboratorObjectId), Integer.valueOf(5)),
                    responseBytes,
                    "POST /rmi HTTP/1.0");

            try (ObjectInputStream response = new ObjectInputStream(withoutHttpHeader(responseBytes.toByteArray()))) {
                assertThat(response.readBoolean()).isTrue();
                assertThat(response.readObject()).isEqualTo(Integer.valueOf(17));
            }
            assertThat(remoteService.invocationCount()).isOne();
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        } finally {
            if (server != null) {
                server.end();
            }
        }
    }

    private static void addTestAndJavassistClassesToDefaultClassPool() {
        ClassPool classPool = ClassPool.getDefault();
        classPool.insertClassPath(new ClassClassPath(AppletServerTest.class));
        classPool.insertClassPath(new ClassClassPath(AppletServer.class));
    }

    private static ByteArrayInputStream rmiRequest(int objectId, int methodId, Object... parameters) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeInt(objectId);
            output.writeInt(methodId);
            output.writeInt(parameters.length);
            for (Object parameter : parameters) {
                output.writeObject(parameter);
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static ByteArrayInputStream withoutHttpHeader(byte[] bytes) {
        byte[] headerEnd = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= bytes.length - headerEnd.length; i++) {
            if (matches(bytes, headerEnd, i)) {
                return new ByteArrayInputStream(bytes, i + headerEnd.length, bytes.length - i - headerEnd.length);
            }
        }
        throw new AssertionError("HTTP response header terminator was not found");
    }

    private static boolean matches(byte[] bytes, byte[] expected, int offset) {
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static int methodId(Class<?> type, String name, Class<?>... parameterTypes) {
        Method[] methods = type.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getName().equals(name) && hasParameterTypes(method, parameterTypes)) {
                return i;
            }
        }
        throw new AssertionError("Method was not found: " + name);
    }

    private static boolean hasParameterTypes(Method method, Class<?>... parameterTypes) {
        Class<?>[] actualParameterTypes = method.getParameterTypes();
        if (actualParameterTypes.length != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (actualParameterTypes[i] != parameterTypes[i]) {
                return false;
            }
        }
        return true;
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class RemoteService {
        private final int baseValue;
        private int invocationCount;

        public RemoteService() {
            this(1);
        }

        public RemoteService(int baseValue) {
            this.baseValue = baseValue;
        }

        public int combineWith(Collaborator collaborator, int increment) {
            invocationCount++;
            return baseValue + collaborator.value() + increment;
        }

        public int invocationCount() {
            return invocationCount;
        }
    }

    public static class Collaborator {
        private final int value;

        public Collaborator() {
            this(1);
        }

        public Collaborator(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
