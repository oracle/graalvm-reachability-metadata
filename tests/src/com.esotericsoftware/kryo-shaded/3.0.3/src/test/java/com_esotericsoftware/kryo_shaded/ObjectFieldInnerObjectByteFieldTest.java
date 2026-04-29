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

public class ObjectFieldInnerObjectByteFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateByteFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<ByteFieldSubject> serializer = new FieldSerializer<>(kryo, ByteFieldSubject.class);
        ByteFieldSubject original = new ByteFieldSubject((byte) -42);

        ByteFieldSubject read = roundTrip(kryo, serializer, original, ByteFieldSubject.class);
        ByteFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectByteField(serializer).getClass().getSimpleName()).isEqualTo("ObjectByteField");
        assertThat(read.marker()).isEqualTo(original.marker());
        assertThat(copy.marker()).isEqualTo(original.marker());
    }

    @Test
    void exposesPrivateByteValueThroughObjectByteFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<ByteFieldSubject> serializer = new FieldSerializer<>(kryo, ByteFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectByteField(serializer);
        ByteFieldSubject subject = new ByteFieldSubject((byte) 127);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.marker());
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

    private static FieldSerializer.CachedField<?> objectByteField(FieldSerializer<ByteFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("marker");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectByteField");
        return cachedField;
    }

    public static class ByteFieldSubject {
        private byte marker;

        public ByteFieldSubject() {
        }

        ByteFieldSubject(byte marker) {
            this.marker = marker;
        }

        byte marker() {
            return marker;
        }
    }

    public static class ByteFieldSubjectConstructorAccess extends ConstructorAccess<ByteFieldSubject> {
        @Override
        public ByteFieldSubject newInstance() {
            return new ByteFieldSubject();
        }

        @Override
        public ByteFieldSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
