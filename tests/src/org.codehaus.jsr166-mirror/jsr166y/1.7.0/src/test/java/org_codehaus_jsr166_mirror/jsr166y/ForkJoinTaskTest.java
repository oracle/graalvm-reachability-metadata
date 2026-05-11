/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jsr166_mirror.jsr166y;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jsr166y.ForkJoinTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinTaskTest {
    @Test
    void serializesAndDeserializesTaskExceptionState() throws Exception {
        SerializableTask task = new SerializableTask("before-serialization");
        RuntimeException originalException = new RuntimeException("serialized failure");
        task.completeExceptionally(originalException);

        byte[] serializedTask = serialize(task);

        SerializableTask deserializedTask = (SerializableTask) deserialize(serializedTask);
        Throwable deserializedException = deserializedTask.getException();

        assertThat(deserializedTask.getRawResult()).isEqualTo("before-serialization");
        assertThat(deserializedException)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("serialized failure");
    }

    @Test
    void copiesExceptionFromAnotherThreadUsingThrowableConstructor() throws Exception {
        SerializableTask task = new SerializableTask("unused");
        ThrowableConstructorException originalException = new ThrowableConstructorException("original failure");

        completeExceptionallyOnAnotherThread(task, originalException);

        Throwable copiedException = task.getException();
        assertThat(copiedException)
                .isInstanceOf(ThrowableConstructorException.class)
                .isNotSameAs(originalException)
                .hasCause(originalException);
    }

    @Test
    void copiesExceptionFromAnotherThreadUsingNoArgConstructor() throws Exception {
        SerializableTask task = new SerializableTask("unused");
        NoArgConstructorException originalException = new NoArgConstructorException();

        completeExceptionallyOnAnotherThread(task, originalException);

        Throwable copiedException = task.getException();
        assertThat(copiedException)
                .isInstanceOf(NoArgConstructorException.class)
                .isNotSameAs(originalException)
                .hasCause(originalException);
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

    private static void completeExceptionallyOnAnotherThread(ForkJoinTask<?> task, RuntimeException exception)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> task.completeExceptionally(exception)).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    public static final class SerializableTask extends ForkJoinTask<String> {
        private static final long serialVersionUID = 1L;

        private String result;

        public SerializableTask(String result) {
            this.result = result;
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
            result = "executed";
            return true;
        }
    }

    public static final class ThrowableConstructorException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ThrowableConstructorException(String message) {
            super(message);
        }

        public ThrowableConstructorException(Throwable cause) {
            super(cause);
        }
    }

    public static final class NoArgConstructorException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NoArgConstructorException() {
        }
    }
}
