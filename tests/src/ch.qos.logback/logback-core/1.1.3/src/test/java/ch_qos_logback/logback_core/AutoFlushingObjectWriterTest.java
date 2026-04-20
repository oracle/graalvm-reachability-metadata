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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AutoFlushingObjectWriterTest {

    @Test
    void writesObjectsFlushesTheStreamAndResetsAtTheConfiguredFrequency() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RecordingObjectOutputStream objectOutputStream = new RecordingObjectOutputStream(outputStream);
        AutoFlushingObjectWriter writer = new AutoFlushingObjectWriter(objectOutputStream, 1);
        ArrayList<String> payload = new ArrayList<>(List.of("first"));

        writer.write(payload);
        payload.add("second");
        writer.write(payload);

        assertThat(objectOutputStream.getFlushCalls()).isEqualTo(2);
        assertThat(objectOutputStream.getResetCalls()).isEqualTo(2);

        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            @SuppressWarnings("unchecked")
            ArrayList<String> firstCopy = (ArrayList<String>) objectInputStream.readObject();
            @SuppressWarnings("unchecked")
            ArrayList<String> secondCopy = (ArrayList<String>) objectInputStream.readObject();

            assertThat(firstCopy).containsExactly("first");
            assertThat(secondCopy).containsExactly("first", "second");
            assertThat(secondCopy).isNotSameAs(firstCopy);
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
