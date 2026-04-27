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
import java.io.Serializable;

import org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.junit.jupiter.api.Test;

public class ValidatingObjectInputStreamTest {

    @Test
    void deserializesAcceptedClassesThroughObjectInputStreamClassResolution() throws Exception {
        final ValidatedPayload payload = new ValidatedPayload(42, true);
        final byte[] serializedPayload = serialize(payload);

        try (ValidatingObjectInputStream inputStream = new ValidatingObjectInputStream(
                new ByteArrayInputStream(serializedPayload))) {
            inputStream.accept(ValidatedPayload.class);

            final Object restored = inputStream.readObject();

            assertThat(restored).isInstanceOf(ValidatedPayload.class);
            final ValidatedPayload restoredPayload = (ValidatedPayload) restored;
            assertThat(restoredPayload.count).isEqualTo(payload.count);
            assertThat(restoredPayload.enabled).isEqualTo(payload.enabled);
        }
    }

    private static byte[] serialize(final Object value) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }

        return outputStream.toByteArray();
    }

    private static final class ValidatedPayload implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int count;

        private final boolean enabled;

        private ValidatedPayload(final int count, final boolean enabled) {
            this.count = count;
            this.enabled = enabled;
        }
    }
}
