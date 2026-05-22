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
    void serializedHttpStringRestoresCaseInsensitiveHashCode() throws Exception {
        HttpString headerName = new HttpString("X-Custom-Header");

        HttpString deserializedHeaderName = deserialize(serialize(headerName));

        assertThat(deserializedHeaderName).isEqualTo(headerName);
        assertThat(deserializedHeaderName.toString()).isEqualTo("X-Custom-Header");
        assertThat(deserializedHeaderName.equalToString("x-custom-header")).isTrue();
        assertThat(deserializedHeaderName.hashCode()).isEqualTo(headerName.hashCode());
    }

    private byte[] serialize(HttpString httpString) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(httpString);
        }
        return bytes.toByteArray();
    }

    private HttpString deserialize(byte[] serializedHttpString) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedHttpString))) {
            Object object = input.readObject();
            assertThat(object).isInstanceOf(HttpString.class);
            return (HttpString) object;
        }
    }
}
