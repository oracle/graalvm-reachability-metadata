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
import org.junit.jupiter.api.Test;

public class ObjectFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateObjectFieldWithReflectionCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<ObjectFieldSubject> serializer = new FieldSerializer<>(kryo, ObjectFieldSubject.class);
        ObjectFieldSubject original = new ObjectFieldSubject("object-field-value");

        ObjectFieldSubject read = roundTrip(kryo, serializer, original, ObjectFieldSubject.class);
        ObjectFieldSubject copy = serializer.copy(kryo, original);

        assertThat(serializer.getField("value").getClass().getSimpleName()).isEqualTo("ObjectField");
        assertThat(read.value()).isEqualTo(original.value());
        assertThat(copy.value()).isEqualTo(original.value());
    }

    @Test
    void preservesNullPrivateObjectField() {
        Kryo kryo = newKryo();
        FieldSerializer<ObjectFieldSubject> serializer = new FieldSerializer<>(kryo, ObjectFieldSubject.class);
        ObjectFieldSubject original = new ObjectFieldSubject(null);

        ObjectFieldSubject read = roundTrip(kryo, serializer, original, ObjectFieldSubject.class);

        assertThat(serializer.getField("value").getClass().getSimpleName()).isEqualTo("ObjectField");
        assertThat(read.value()).isNull();
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

    public static class ObjectFieldSubject {
        private Object value;

        public ObjectFieldSubject() {
        }

        ObjectFieldSubject(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }
    }

    public static class ObjectFieldSubjectConstructorAccess extends ConstructorAccess<ObjectFieldSubject> {
        @Override
        public ObjectFieldSubject newInstance() {
            return new ObjectFieldSubject();
        }

        @Override
        public ObjectFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
