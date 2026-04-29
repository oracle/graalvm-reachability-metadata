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

public class ObjectFieldInnerObjectShortFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateShortFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<ShortFieldSubject> serializer = new FieldSerializer<>(kryo, ShortFieldSubject.class);
        ShortFieldSubject original = new ShortFieldSubject((short) -12345);

        ShortFieldSubject read = roundTrip(kryo, serializer, original, ShortFieldSubject.class);
        ShortFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectShortField(serializer).getClass().getSimpleName()).isEqualTo("ObjectShortField");
        assertThat(read.priority()).isEqualTo(original.priority());
        assertThat(copy.priority()).isEqualTo(original.priority());
    }

    @Test
    void exposesPrivateShortValueThroughObjectShortFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<ShortFieldSubject> serializer = new FieldSerializer<>(kryo, ShortFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectShortField(serializer);
        ShortFieldSubject subject = new ShortFieldSubject((short) 12345);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.priority());
    }

    @Test
    void serializesAndReadsShortFieldAsFixedWidthValue() {
        Kryo kryo = newKryo();
        FieldSerializer<ShortFieldSubject> serializer = new FieldSerializer<>(kryo, ShortFieldSubject.class);
        ShortFieldSubject original = new ShortFieldSubject(Short.MIN_VALUE);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        ShortFieldSubject read = serializer.read(kryo, input, ShortFieldSubject.class);

        assertThat(output.position()).isEqualTo(Short.BYTES);
        assertThat(read.priority()).isEqualTo(original.priority());
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

    private static FieldSerializer.CachedField<?> objectShortField(FieldSerializer<ShortFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("priority");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectShortField");
        return cachedField;
    }

    public static class ShortFieldSubject {
        private short priority;

        public ShortFieldSubject() {
        }

        ShortFieldSubject(short priority) {
            this.priority = priority;
        }

        short priority() {
            return priority;
        }
    }

    public static class ShortFieldSubjectConstructorAccess extends ConstructorAccess<ShortFieldSubject> {
        @Override
        public ShortFieldSubject newInstance() {
            return new ShortFieldSubject();
        }

        @Override
        public ShortFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
