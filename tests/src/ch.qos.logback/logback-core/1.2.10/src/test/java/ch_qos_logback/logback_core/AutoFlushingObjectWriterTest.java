/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.net.AutoFlushingObjectWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class AutoFlushingObjectWriterTest {

    @Test
    void writesObjectsFlushesTheStreamAndResetsAtTheConfiguredFrequency() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RecordingObjectOutputStream objectOutputStream = new RecordingObjectOutputStream(outputStream);
        AutoFlushingObjectWriter writer = new AutoFlushingObjectWriter(objectOutputStream, 2);

        writer.write("first event");
        writer.write("second event");

        assertThat(objectOutputStream.getFlushCalls()).isEqualTo(2);
        assertThat(objectOutputStream.getResetCalls()).isEqualTo(1);

        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            assertThat(objectInputStream.readObject()).isEqualTo("first event");
            assertThat(objectInputStream.readObject()).isEqualTo("second event");
        }
    }

    private static final class RecordingObjectOutputStream extends ObjectOutputStream {

        private int flushCalls;
        private int resetCalls;

        private RecordingObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        public void flush() throws IOException {
            flushCalls++;
            super.flush();
        }

        @Override
        public void reset() throws IOException {
            resetCalls++;
            super.reset();
        }

        private int getFlushCalls() {
            return flushCalls;
        }

        private int getResetCalls() {
            return resetCalls;
        }
    }
}
