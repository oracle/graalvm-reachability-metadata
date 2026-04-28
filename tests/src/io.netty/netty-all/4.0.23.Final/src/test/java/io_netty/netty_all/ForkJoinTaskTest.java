/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.netty.util.internal.chmv8.ForkJoinTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ForkJoinTaskTest {
    @Test
    void serializationRoundTripPreservesCompletedTaskResult() throws Exception {
        SerializableStringTask original = new SerializableStringTask("task-result");

        String result = original.invoke();
        ForkJoinTask<String> restored = deserialize(serialize(original));

        Assertions.assertEquals("task-result", result);
        Assertions.assertTrue(restored.isDone());
        Assertions.assertTrue(restored.isCompletedNormally());
        Assertions.assertEquals("task-result", restored.join());
    }

    private static byte[] serialize(ForkJoinTask<String> task) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(task);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ForkJoinTask<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ForkJoinTask<String>) in.readObject();
        }
    }

    public static final class SerializableStringTask extends ForkJoinTask<String> {
        private static final long serialVersionUID = 1L;

        private final String value;
        private String result;

        public SerializableStringTask(String value) {
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
