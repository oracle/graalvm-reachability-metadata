/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Anonymous1Test {

    private static final String BASE64_CLASS_NAME = "io.undertow.websockets.core.protocol.version07.Base64";

    @Test
    void decodesSerializedObjectWithExplicitClassLoader() throws Exception {
        List<String> payload = new ArrayList<>();
        payload.add("undertow");
        payload.add("websocket");
        payload.add("base64");
        String encodedPayload = serializeToBase64(payload);

        Object decoded = decodeToObject(encodedPayload, Base64Anonymous1Test.class.getClassLoader());

        assertThat(decoded).isEqualTo(payload);
    }

    private static String serializeToBase64(Object payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            objectOutputStream.writeObject(payload);
        }
        return new String(Base64.getEncoder().encode(bytes.toByteArray()), StandardCharsets.US_ASCII);
    }

    private static Object decodeToObject(String encodedPayload, ClassLoader loader) throws Exception {
        Method decodeToObjectMethod = base64Class().getDeclaredMethod(
                "decodeToObject", String.class, int.class, ClassLoader.class);
        decodeToObjectMethod.setAccessible(true);
        return decodeToObjectMethod.invoke(null, encodedPayload, 0, loader);
    }

    private static Class<?> base64Class() throws ClassNotFoundException {
        return Class.forName(BASE64_CLASS_NAME);
    }
}
