/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.util.HttpString;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpStringTest {
    @Test
    void deserializationRestoresTransientHashCode() throws Exception {
        HttpString original = new HttpString("X-Custom-Header");

        HttpString deserialized = deserialize(serialize(original), HttpString.class);

        assertThat(deserialized).isNotSameAs(original);
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.toString()).isEqualTo("X-Custom-Header");
        assertThat(deserialized.hashCode()).isEqualTo(original.hashCode());
        assertThat(deserialized.hashCode()).isEqualTo(new HttpString("x-custom-header").hashCode());
    }

    private static byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static <T> T deserialize(byte[] bytes, Class<T> resultType) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return resultType.cast(input.readObject());
        }
    }
}
