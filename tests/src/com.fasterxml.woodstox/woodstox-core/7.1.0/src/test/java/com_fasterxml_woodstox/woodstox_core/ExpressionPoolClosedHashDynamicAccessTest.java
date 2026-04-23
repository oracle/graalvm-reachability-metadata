/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.Expression;
import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool.ClosedHash;
import com.ctc.wstx.shaded.msv_core.grammar.SimpleNameClass;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class ExpressionPoolClosedHashDynamicAccessTest {
    private static final MethodHandle CLOSED_HASH_READ_OBJECT = closedHashReadObjectHandle();
    private static final VarHandle CLOSED_HASH_COUNT = closedHashCountHandle();

    @Test
    void serializesAndDeserializesClosedHashEntries() throws Exception {
        ExpressionPool pool = new ExpressionPool();
        Expression attribute = pool.createAttribute(new SimpleNameClass("urn:test", "value"));
        Expression repeatedAttribute = pool.createOneOrMore(attribute);
        Expression mixedAttribute = pool.createMixed(attribute);
        Expression sequence = pool.createSequence(repeatedAttribute, mixedAttribute);
        Expression choice = pool.createChoice(sequence, attribute);

        ClosedHash parent = new ClosedHash();
        parent.put(attribute);
        parent.put(repeatedAttribute);

        ClosedHash hash = new ClosedHash(parent);
        hash.put(mixedAttribute);
        hash.put(sequence);
        hash.put(choice);

        assertThat(hash.get(attribute)).isSameAs(attribute);
        assertThat(hash.get(choice)).isSameAs(choice);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(hash);
        }

        ClosedHash restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ClosedHash) input.readObject();
        }

        Expression restoredEntry = pool.createList(choice);
        restored.put(restoredEntry);

        assertThat(bytes.toByteArray()).isNotEmpty();
        assertThat(restored).isNotNull();
        assertThat(restored.get(restoredEntry)).isSameAs(restoredEntry);
    }

    @Test
    void readsVersionOneClosedHashEntries() throws Throwable {
        ExpressionPool pool = new ExpressionPool();
        Expression streamedExpression = pool.createAttribute(new SimpleNameClass("urn:test", "deserialized"));
        ClosedHash hash = new ClosedHash();

        VersionOneClosedHashInputStream input = new VersionOneClosedHashInputStream(hash, streamedExpression);
        try {
            CLOSED_HASH_READ_OBJECT.invoke(hash, input);
        } finally {
            input.stopCountWriter();
        }

        assertThat(hash.get(streamedExpression)).isSameAs(streamedExpression);
    }

    private static MethodHandle closedHashReadObjectHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClosedHash.class, MethodHandles.lookup());
            return lookup.findSpecial(
                    ClosedHash.class,
                    "readObject",
                    MethodType.methodType(void.class, ObjectInputStream.class),
                    ClosedHash.class
            );
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new ExceptionInInitializerError(reflectiveOperationException);
        }
    }

    private static VarHandle closedHashCountHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClosedHash.class, MethodHandles.lookup());
            return lookup.findVarHandle(ClosedHash.class, "count", int.class);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new ExceptionInInitializerError(reflectiveOperationException);
        }
    }

    private static final class VersionOneClosedHashInputStream extends ObjectInputStream {
        private final ClosedHash target;
        private final Expression streamedExpression;
        private final AtomicBoolean keepCountPositive = new AtomicBoolean(false);
        private Thread countWriter;

        private VersionOneClosedHashInputStream(ClosedHash target, Expression streamedExpression) throws IOException {
            this.target = target;
            this.streamedExpression = streamedExpression;
        }

        @Override
        public GetField readFields() {
            startCountWriter();
            return new VersionOneClosedHashGetField();
        }

        @Override
        protected Object readObjectOverride() throws IOException {
            stopCountWriter();
            CLOSED_HASH_COUNT.setVolatile(target, 0);
            return streamedExpression;
        }

        private void startCountWriter() {
            if (countWriter != null) {
                return;
            }
            keepCountPositive.set(true);
            countWriter = new Thread(() -> {
                while (keepCountPositive.get()) {
                    CLOSED_HASH_COUNT.setVolatile(target, 1);
                    Thread.onSpinWait();
                }
            }, "closed-hash-count-writer");
            countWriter.setDaemon(true);
            countWriter.start();
        }

        private void stopCountWriter() throws IOException {
            if (countWriter == null) {
                return;
            }
            keepCountPositive.set(false);
            try {
                countWriter.join();
                countWriter = null;
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping count writer", interruptedException);
            }
        }

        private static final class VersionOneClosedHashGetField extends GetField {
            @Override
            public ObjectStreamClass getObjectStreamClass() {
                return ObjectStreamClass.lookup(ClosedHash.class);
            }

            @Override
            public boolean defaulted(String name) {
                return false;
            }

            @Override
            public boolean get(String name, boolean value) {
                return value;
            }

            @Override
            public byte get(String name, byte value) {
                if ("streamVersion".equals(name)) {
                    return 1;
                }
                return value;
            }

            @Override
            public char get(String name, char value) {
                return value;
            }

            @Override
            public short get(String name, short value) {
                return value;
            }

            @Override
            public int get(String name, int value) {
                if ("count".equals(name)) {
                    return 1;
                }
                return value;
            }

            @Override
            public long get(String name, long value) {
                return value;
            }

            @Override
            public float get(String name, float value) {
                return value;
            }

            @Override
            public double get(String name, double value) {
                return value;
            }

            @Override
            public Object get(String name, Object value) {
                return value;
            }
        }
    }
}
