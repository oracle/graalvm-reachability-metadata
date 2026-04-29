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
import com.esotericsoftware.reflectasm.ConstructorAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectIntFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateIntFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<IntFieldSubject> serializer = new FieldSerializer<>(kryo, IntFieldSubject.class);
        IntFieldSubject original = new IntFieldSubject(-123456);

        IntFieldSubject read = roundTrip(kryo, serializer, original, IntFieldSubject.class);
        IntFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectIntField(serializer).getClass().getSimpleName()).isEqualTo("ObjectIntField");
        assertThat(read.count()).isEqualTo(original.count());
        assertThat(copy.count()).isEqualTo(original.count());
    }

    @Test
    void exposesPrivateIntValueThroughObjectIntFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<IntFieldSubject> serializer = new FieldSerializer<>(kryo, IntFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectIntField(serializer);
        IntFieldSubject subject = new IntFieldSubject(98765);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.count());
    }

    @Test
    void serializesAndReadsPrivateIntFieldWithFixedWidthEncoding() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<IntFieldSubject> serializer = new FieldSerializer<>(kryo, IntFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectIntField(serializer);
        setVarIntsEnabled(cachedField, false);
        IntFieldSubject original = new IntFieldSubject(Integer.MIN_VALUE + 17);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        IntFieldSubject read = serializer.read(kryo, input, IntFieldSubject.class);

        assertThat(output.position()).isEqualTo(Integer.BYTES);
        assertThat(read.count()).isEqualTo(original.count());
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

    private static FieldSerializer.CachedField<?> objectIntField(FieldSerializer<IntFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("count");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectIntField");
        return cachedField;
    }

    private static void setVarIntsEnabled(FieldSerializer.CachedField<?> cachedField, boolean enabled) throws Exception {
        Field varIntsEnabled = FieldSerializer.CachedField.class.getDeclaredField("varIntsEnabled");
        varIntsEnabled.setAccessible(true);
        varIntsEnabled.setBoolean(cachedField, enabled);
    }

    public static class IntFieldSubject {
        private int count;

        public IntFieldSubject() {
        }

        IntFieldSubject(int count) {
            this.count = count;
        }

        int count() {
            return count;
        }
    }

    public static class IntFieldSubjectConstructorAccess extends ConstructorAccess<IntFieldSubject> {
        @Override
        public IntFieldSubject newInstance() {
            return new IntFieldSubject();
        }

        @Override
        public IntFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
