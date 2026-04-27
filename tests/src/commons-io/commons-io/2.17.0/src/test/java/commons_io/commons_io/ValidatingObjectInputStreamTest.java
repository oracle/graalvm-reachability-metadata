/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.junit.jupiter.api.Test;

public class ValidatingObjectInputStreamTest {

    @Test
    void deserializesAcceptedClassesThroughObjectInputStreamClassResolution() throws Exception {
        ArrayList<String> payload = new ArrayList<>(List.of("alpha", "beta", "gamma"));
        byte[] serializedPayload = serialize(payload);

        try (ValidatingObjectInputStream inputStream = new ValidatingObjectInputStream(
                new ByteArrayInputStream(serializedPayload))) {
            inputStream.accept(ArrayList.class);

            Object restored = inputStream.readObject();

            assertThat(restored).isInstanceOf(ArrayList.class);
            assertThat(restored).isEqualTo(payload);
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }

        return outputStream.toByteArray();
    }
}
