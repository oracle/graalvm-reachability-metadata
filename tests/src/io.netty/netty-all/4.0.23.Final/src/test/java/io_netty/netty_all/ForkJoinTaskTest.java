/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.chmv8.ForkJoinTask;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinTaskTest {
    @Test
    void serializesAndDeserializesAdaptedRunnableTask() throws Exception {
        ForkJoinTask<String> task = ForkJoinTask.adapt(new SerializableNoOpRunnable(), "completed");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(task);
        }

        Object deserialized;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserialized = input.readObject();
        }

        assertThat(deserialized).isInstanceOf(ForkJoinTask.class);
        @SuppressWarnings("unchecked")
        ForkJoinTask<String> restored = (ForkJoinTask<String>) deserialized;
        assertThat(restored.isDone()).isFalse();
        assertThat(restored.invoke()).isEqualTo("completed");
        assertThat(restored.isCompletedNormally()).isTrue();
    }

    private static final class SerializableNoOpRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
        }
    }
}
