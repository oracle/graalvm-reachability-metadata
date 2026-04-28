/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectLongFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateLongFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<LongFieldSubject> serializer = new FieldSerializer<>(kryo, LongFieldSubject.class);
        LongFieldSubject original = new LongFieldSubject(-9_876_543_210_123L);

        LongFieldSubject read = roundTrip(kryo, serializer, original, LongFieldSubject.class);
        LongFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectLongField(serializer).getClass().getSimpleName()).isEqualTo("ObjectLongField");
        assertThat(read.sequence()).isEqualTo(original.sequence());
        assertThat(copy.sequence()).isEqualTo(original.sequence());
    }

    @Test
    void exposesPrivateLongValueThroughObjectLongFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<LongFieldSubject> serializer = new FieldSerializer<>(kryo, LongFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectLongField(serializer);
        LongFieldSubject subject = new LongFieldSubject(9_876_543_210_123L);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.sequence());
    }

    @Test
    void serializesAndReadsPrivateLongFieldWithFixedWidthEncoding() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<LongFieldSubject> serializer = new FieldSerializer<>(kryo, LongFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectLongField(serializer);
        setVarIntsEnabled(cachedField, false);
        LongFieldSubject original = new LongFieldSubject(Long.MIN_VALUE + 123_456L);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        LongFieldSubject read = serializer.read(kryo, input, LongFieldSubject.class);

        assertThat(output.position()).isEqualTo(Long.BYTES);
        assertThat(read.sequence()).isEqualTo(original.sequence());
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);
        return kryo;
    }

    private static <T> T roundTrip(Kryo kryo, FieldSerializer<T> serializer, T original, Class<T> type) {
        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        T read = serializer.read(kryo, input, type);
        kryo.reset();
        return read;
    }

    private static FieldSerializer.CachedField<?> objectLongField(FieldSerializer<LongFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("sequence");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectLongField");
        return cachedField;
    }

    private static void setVarIntsEnabled(
            FieldSerializer.CachedField<?> cachedField, boolean enabled) throws Exception {
        Field varIntsEnabled = FieldSerializer.CachedField.class.getDeclaredField("varIntsEnabled");
        varIntsEnabled.setAccessible(true);
        varIntsEnabled.setBoolean(cachedField, enabled);
    }

    public static class LongFieldSubject {
        private long sequence;

        public LongFieldSubject() {
        }

        LongFieldSubject(long sequence) {
            this.sequence = sequence;
        }

        long sequence() {
            return sequence;
        }
    }
}
