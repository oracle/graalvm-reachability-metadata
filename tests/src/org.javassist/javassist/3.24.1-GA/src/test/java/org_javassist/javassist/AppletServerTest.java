/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javassist.tools.rmi.AppletServer;

import org.junit.jupiter.api.Test;

public class AppletServerTest {
    private static final byte[] HTTP_OK = "HTTP/1.0 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rmiRequestInvokesExportedObjectAndSerializesReturnValue() throws Exception {
        AppletServer server = new AppletServer(0);
        try {
            RemoteCalculator calculator = new RemoteCalculator();
            int objectId = server.exportObject("calculator", calculator);
            int methodId = methodId(calculator, "doubleValue", int.class);

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            server.doReply(rmiRequest(objectId, methodId, 21), response, "POST /rmi HTTP/1.0");

            assertThat(calculator.lastValue()).isEqualTo(21);
            assertThat(successfulRmiResult(response.toByteArray())).isEqualTo(42);
        } finally {
            server.end();
        }
    }

    public static class RemoteCalculator {
        private int lastValue;

        public int doubleValue(int value) {
            lastValue = value;
            return value * 2;
        }

        int lastValue() {
            return lastValue;
        }
    }

    private static int methodId(Object exportedObject, String methodName, Class<?>... parameterTypes) throws Exception {
        Method[] methods = exportedObject.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getName().equals(methodName)
                    && method.getParameterTypes().length == parameterTypes.length
                    && hasParameterTypes(method, parameterTypes)) {
                return i;
            }
        }
        throw new IllegalStateException("Method not found: " + methodName);
    }

    private static boolean hasParameterTypes(Method method, Class<?>[] parameterTypes) {
        Class<?>[] actualParameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!actualParameterTypes[i].equals(parameterTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private static ByteArrayInputStream rmiRequest(int objectId, int methodId, Object parameter) throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(request)) {
            output.writeInt(objectId);
            output.writeInt(methodId);
            output.writeInt(1);
            output.writeObject(parameter);
        }
        return new ByteArrayInputStream(request.toByteArray());
    }

    private static Object successfulRmiResult(byte[] response) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(response);
        assertThat(input.readNBytes(HTTP_OK.length)).isEqualTo(HTTP_OK);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            assertThat(objectInput.readBoolean()).isTrue();
            return objectInput.readObject();
        }
    }
}
