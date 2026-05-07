/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    private static final String BASE64_CLASS_NAME = "io.undertow.websockets.core.protocol.version07.Base64";

    @Test
    void serializesAndDeserializesObjectsThroughBase64() throws Exception {
        Class<?> base64Class = Class.forName(BASE64_CLASS_NAME);
        Method encodeObject = base64Class.getDeclaredMethod("encodeObject", Serializable.class);
        Method decodeToObject = base64Class.getDeclaredMethod("decodeToObject", String.class);
        encodeObject.setAccessible(true);
        decodeToObject.setAccessible(true);

        String message = "undertow websocket base64 serialization";
        String encoded = invokeStatic(encodeObject, message, String.class);
        Object decoded = invokeStatic(decodeToObject, encoded, Object.class);

        assertThat(encoded).isNotBlank();
        assertThat(decoded).isEqualTo(message);
    }

    private static <T> T invokeStatic(Method method, Object argument, Class<T> resultType) throws Exception {
        try {
            return resultType.cast(method.invoke(null, argument));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }
}
