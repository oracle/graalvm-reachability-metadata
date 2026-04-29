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

public class ObjectFieldInnerObjectCharFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateCharFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<CharFieldSubject> serializer = new FieldSerializer<>(kryo, CharFieldSubject.class);
        CharFieldSubject original = new CharFieldSubject('\u03A9');

        CharFieldSubject read = roundTrip(kryo, serializer, original, CharFieldSubject.class);
        CharFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectCharField(serializer).getClass().getSimpleName()).isEqualTo("ObjectCharField");
        assertThat(read.marker()).isEqualTo(original.marker());
        assertThat(copy.marker()).isEqualTo(original.marker());
    }

    @Test
    void exposesPrivateCharValueThroughObjectCharFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<CharFieldSubject> serializer = new FieldSerializer<>(kryo, CharFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectCharField(serializer);
        CharFieldSubject subject = new CharFieldSubject('\u5B57');

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.marker());
    }

    @Test
    void serializesAndReadsCharFieldAsFixedWidthValue() {
        Kryo kryo = newKryo();
        FieldSerializer<CharFieldSubject> serializer = new FieldSerializer<>(kryo, CharFieldSubject.class);
        CharFieldSubject original = new CharFieldSubject(Character.MAX_VALUE);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        CharFieldSubject read = serializer.read(kryo, input, CharFieldSubject.class);

        assertThat(output.position()).isEqualTo(Character.BYTES);
        assertThat(read.marker()).isEqualTo(original.marker());
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

    private static FieldSerializer.CachedField<?> objectCharField(FieldSerializer<CharFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("marker");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectCharField");
        return cachedField;
    }

    public static class CharFieldSubject {
        private char marker;

        public CharFieldSubject() {
        }

        CharFieldSubject(char marker) {
            this.marker = marker;
        }

        char marker() {
            return marker;
        }
    }

    public static class CharFieldSubjectConstructorAccess extends ConstructorAccess<CharFieldSubject> {
        @Override
        public CharFieldSubject newInstance() {
            return new CharFieldSubject();
        }

        @Override
        public CharFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
