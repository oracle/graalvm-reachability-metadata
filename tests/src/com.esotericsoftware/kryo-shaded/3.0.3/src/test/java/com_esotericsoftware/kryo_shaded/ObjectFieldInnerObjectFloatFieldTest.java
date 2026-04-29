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
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectFloatFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateFloatFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<FloatFieldSubject> serializer = new FieldSerializer<>(kryo, FloatFieldSubject.class);
        FloatFieldSubject original = new FloatFieldSubject(-12345.625f);

        FloatFieldSubject read = roundTrip(kryo, serializer, original, FloatFieldSubject.class);
        FloatFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectFloatField(serializer).getClass().getSimpleName()).isEqualTo("ObjectFloatField");
        assertThat(read.measurement()).isEqualTo(original.measurement());
        assertThat(copy.measurement()).isEqualTo(original.measurement());
    }

    @Test
    void exposesPrivateFloatValueThroughObjectFloatFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<FloatFieldSubject> serializer = new FieldSerializer<>(kryo, FloatFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectFloatField(serializer);
        FloatFieldSubject subject = new FloatFieldSubject(9876.5f);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.measurement());
    }

    @Test
    void serializesPrivateFloatFieldWithFixedWidthEncoding() {
        Kryo kryo = newKryo();
        FieldSerializer<FloatFieldSubject> serializer = new FieldSerializer<>(kryo, FloatFieldSubject.class);
        FloatFieldSubject original = new FloatFieldSubject(Float.MIN_NORMAL);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        FloatFieldSubject read = serializer.read(kryo, input, FloatFieldSubject.class);

        assertThat(objectFloatField(serializer).getClass().getSimpleName()).isEqualTo("ObjectFloatField");
        assertThat(output.position()).isEqualTo(Float.BYTES);
        assertThat(read.measurement()).isEqualTo(original.measurement());
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

    private static FieldSerializer.CachedField<?> objectFloatField(FieldSerializer<FloatFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("measurement");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectFloatField");
        return cachedField;
    }

    public static class FloatFieldSubject {
        private float measurement;

        public FloatFieldSubject() {
        }

        FloatFieldSubject(float measurement) {
            this.measurement = measurement;
        }

        float measurement() {
            return measurement;
        }
    }

    public static class FloatFieldSubjectConstructorAccess extends ConstructorAccess<FloatFieldSubject> {
        @Override
        public FloatFieldSubject newInstance() {
            return new FloatFieldSubject();
        }

        @Override
        public FloatFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
