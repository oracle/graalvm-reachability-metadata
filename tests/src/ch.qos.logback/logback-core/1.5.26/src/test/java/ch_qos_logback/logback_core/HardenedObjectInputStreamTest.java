/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.net.HardenedObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HardenedObjectInputStreamTest {
    @Test
    void readObjectResolvesWhitelistedJavaUtilClass() throws IOException, ClassNotFoundException {
        List<String> payload = new ArrayList<>();
        payload.add("logback-hardened-input-stream");

        try (HardenedObjectInputStream input = new HardenedObjectInputStream(
                new ByteArrayInputStream(serialize(payload)), new String[0])) {
            assertThat(input.readObject()).isEqualTo(payload);
        }
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(object);
        }
        return output.toByteArray();
    }
}
