/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.Closure;
import io.sundr.deps.org.apache.commons.collections.ClosureUtils;
import io.sundr.deps.org.apache.commons.collections.Predicate;
import io.sundr.deps.org.apache.commons.collections.functors.WhileClosure;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class WhileClosureTest {
    private static final String UNSAFE_SERIALIZATION_PROPERTY =
            "io.sundr.deps.org.apache.commons.collections.enableUnsafeSerialization";

    @Test
    public void executesWhilePredicateMatchesAndSupportsOptInSerialization()
            throws IOException, ClassNotFoundException {
        String previousValue = System.getProperty(UNSAFE_SERIALIZATION_PROPERTY);
        System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, "true");
        try {
            CountingPredicate predicate = new CountingPredicate(2);
            CountingClosure wrapped = new CountingClosure();
            Closure closure = ClosureUtils.whileClosure(predicate, wrapped);

            closure.execute("before-serialization");
            WhileClosure restored = (WhileClosure) deserialize(serialize(closure));
            CountingPredicate restoredPredicate = (CountingPredicate) restored.getPredicate();
            CountingClosure restoredWrapped = (CountingClosure) restored.getClosure();
            restored.execute("after-serialization");

            assertThat(closure).isInstanceOf(WhileClosure.class);
            assertThat(((WhileClosure) closure).isDoLoop()).isFalse();
            assertThat(predicate.evaluationCount()).isEqualTo(3);
            assertThat(wrapped.executionCount()).isEqualTo(2);
            assertThat(wrapped.lastInput()).isEqualTo("before-serialization");
            assertThat(restored.isDoLoop()).isFalse();
            assertThat(restoredPredicate.evaluationCount()).isEqualTo(4);
            assertThat(restoredWrapped.executionCount()).isEqualTo(2);
        } finally {
            restoreProperty(previousValue);
        }
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static void restoreProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(UNSAFE_SERIALIZATION_PROPERTY);
        } else {
            System.setProperty(UNSAFE_SERIALIZATION_PROPERTY, previousValue);
        }
    }

    public static final class CountingPredicate implements Predicate, Serializable {
        private static final long serialVersionUID = 1L;

        private int remainingMatches;
        private int evaluationCount;

        public CountingPredicate(int remainingMatches) {
            this.remainingMatches = remainingMatches;
        }

        @Override
        public boolean evaluate(Object input) {
            evaluationCount++;
            if (remainingMatches > 0) {
                remainingMatches--;
                return true;
            }
            return false;
        }

        int evaluationCount() {
            return evaluationCount;
        }
    }

    public static final class CountingClosure implements Closure, Serializable {
        private static final long serialVersionUID = 1L;

        private int executionCount;
        private Object lastInput;

        @Override
        public void execute(Object input) {
            executionCount++;
            lastInput = input;
        }

        int executionCount() {
            return executionCount;
        }

        Object lastInput() {
            return lastInput;
        }
    }
}
