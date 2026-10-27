/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.internal.chmv8.ForkJoinTask;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinTaskTest {
    @Test
    public void completedTaskCanBeSerializedAndDeserialized() throws IOException, ClassNotFoundException {
        SerializableTask task = new SerializableTask("netty");

        assertThat(task.invoke()).isEqualTo("netty");

        SerializableTask deserialized = deserialize(serialize(task));

        assertThat(deserialized.isCompletedNormally()).isTrue();
        assertThat(deserialized.join()).isEqualTo("netty");
    }

    private static byte[] serialize(SerializableTask task) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(task);
        }
        return bytes.toByteArray();
    }

    private static SerializableTask deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SerializableTask) input.readObject();
        }
    }

    public static final class SerializableTask extends ForkJoinTask<String> {
        private final String value;
        private String result;

        private SerializableTask(String value) {
            this.value = value;
        }

        @Override
        public String getRawResult() {
            return result;
        }

        @Override
        protected void setRawResult(String value) {
            result = value;
        }

        @Override
        protected boolean exec() {
            result = value;
            return true;
        }
    }
}
