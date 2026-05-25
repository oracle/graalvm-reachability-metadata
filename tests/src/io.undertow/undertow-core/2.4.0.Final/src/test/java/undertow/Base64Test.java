/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {

    private static final String BASE64_CLASS_NAME = "io.undertow.websockets.core.protocol.version07.Base64";

    @Test
    void encodesAndDecodesSerializableObject() throws Exception {
        String payload = "undertow-websocket-base64";

        String encoded = encodeObject(payload);
        Object decoded = decodeToObject(encoded);

        assertThat(encoded).isNotBlank();
        assertThat(decoded).isEqualTo(payload);
    }

    private static String encodeObject(Serializable payload) throws Exception {
        Method encodeObjectMethod = base64Class().getDeclaredMethod("encodeObject", Serializable.class);
        encodeObjectMethod.setAccessible(true);
        return (String) encodeObjectMethod.invoke(null, payload);
    }

    private static Object decodeToObject(String encoded) throws Exception {
        Method decodeToObjectMethod = base64Class().getDeclaredMethod("decodeToObject", String.class);
        decodeToObjectMethod.setAccessible(true);
        return decodeToObjectMethod.invoke(null, encoded);
    }

    private static Class<?> base64Class() throws ClassNotFoundException {
        return Class.forName(BASE64_CLASS_NAME);
    }
}
