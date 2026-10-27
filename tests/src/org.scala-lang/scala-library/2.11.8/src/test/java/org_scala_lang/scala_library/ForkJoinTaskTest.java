/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import scala.concurrent.forkjoin.RecursiveTask;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinTaskTest {

    @Test
    void preservesACompletedTaskResultDuringSerialization() throws Exception {
        AdditionTask original = new AdditionTask(19, 23);
        assertThat(original.invoke()).isEqualTo(42);

        AdditionTask restored = roundTrip(original);

        assertThat(restored.isCompletedNormally()).isTrue();
        assertThat(restored.join()).isEqualTo(42);
    }

    private static AdditionTask roundTrip(AdditionTask task) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(task);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (AdditionTask) input.readObject();
        }
    }

    public static final class AdditionTask extends RecursiveTask<Integer> {
        private static final long serialVersionUID = 1L;

        private final int left;
        private final int right;

        private AdditionTask(int left, int right) {
            this.left = left;
            this.right = right;
        }

        @Override
        protected Integer compute() {
            return left + right;
        }
    }
}
