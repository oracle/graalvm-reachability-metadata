/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.net.AutoFlushingObjectWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoFlushingObjectWriterTest {
    @Test
    void writeSerializesObjectAndFlushesStream() throws IOException, ClassNotFoundException {
        CountingOutputStream output = new CountingOutputStream();
        String message = "auto flushing object writer payload";

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            AutoFlushingObjectWriter writer = new AutoFlushingObjectWriter(objectOutputStream, 2);
            int flushCountBeforeWrite = output.flushCount();

            writer.write(message);

            assertThat(output.flushCount()).isGreaterThan(flushCountBeforeWrite);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(message);
        }
    }

    private static final class CountingOutputStream extends ByteArrayOutputStream {
        private int flushCount;

        @Override
        public void flush() throws IOException {
            super.flush();
            flushCount++;
        }

        int flushCount() {
            return flushCount;
        }
    }
}
