/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.net.AutoFlushingObjectWriter;
import ch.qos.logback.core.net.ObjectWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoFlushingObjectWriterTest {

    @Test
    void writeSerializesObjectThroughObjectOutputStream() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            int headerSize = output.size();
            ObjectWriter writer = new AutoFlushingObjectWriter(objectOutputStream, 1);

            writer.write("logback-payload");

            byte[] serializedBytes = output.toByteArray();
            assertThat(serializedBytes.length).isGreaterThan(headerSize);
            assertThat(new String(serializedBytes, StandardCharsets.ISO_8859_1)).contains("logback-payload");
        }
    }
}
