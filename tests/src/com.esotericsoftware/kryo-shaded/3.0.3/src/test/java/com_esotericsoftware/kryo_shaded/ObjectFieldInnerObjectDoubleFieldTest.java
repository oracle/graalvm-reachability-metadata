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

public class ObjectFieldInnerObjectDoubleFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateDoubleFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<DoubleFieldSubject> serializer = new FieldSerializer<>(kryo, DoubleFieldSubject.class);
        DoubleFieldSubject original = new DoubleFieldSubject(-12345.6789d);

        DoubleFieldSubject read = roundTrip(kryo, serializer, original, DoubleFieldSubject.class);
        DoubleFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectDoubleField(serializer).getClass().getSimpleName()).isEqualTo("ObjectDoubleField");
        assertThat(read.measurement()).isEqualTo(original.measurement());
        assertThat(copy.measurement()).isEqualTo(original.measurement());
    }

    @Test
    void exposesPrivateDoubleValueThroughObjectDoubleFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<DoubleFieldSubject> serializer = new FieldSerializer<>(kryo, DoubleFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectDoubleField(serializer);
        DoubleFieldSubject subject = new DoubleFieldSubject(98765.4321d);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.measurement());
    }

    @Test
    void serializesPrivateDoubleFieldWithFixedWidthEncoding() {
        Kryo kryo = newKryo();
        FieldSerializer<DoubleFieldSubject> serializer = new FieldSerializer<>(kryo, DoubleFieldSubject.class);
        DoubleFieldSubject original = new DoubleFieldSubject(Double.MIN_NORMAL);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        DoubleFieldSubject read = serializer.read(kryo, input, DoubleFieldSubject.class);

        assertThat(objectDoubleField(serializer).getClass().getSimpleName()).isEqualTo("ObjectDoubleField");
        assertThat(output.position()).isEqualTo(Double.BYTES);
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

    private static FieldSerializer.CachedField<?> objectDoubleField(FieldSerializer<DoubleFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("measurement");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectDoubleField");
        return cachedField;
    }

    public static class DoubleFieldSubject {
        private double measurement;

        public DoubleFieldSubject() {
        }

        DoubleFieldSubject(double measurement) {
            this.measurement = measurement;
        }

        double measurement() {
            return measurement;
        }
    }

    public static class DoubleFieldSubjectConstructorAccess extends ConstructorAccess<DoubleFieldSubject> {
        @Override
        public DoubleFieldSubject newInstance() {
            return new DoubleFieldSubject();
        }

        @Override
        public DoubleFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
