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
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectBooleanFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateBooleanFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<BooleanFieldSubject> serializer = new FieldSerializer<>(kryo, BooleanFieldSubject.class);
        BooleanFieldSubject original = new BooleanFieldSubject(true);

        BooleanFieldSubject read = roundTrip(kryo, serializer, original, BooleanFieldSubject.class);
        BooleanFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectBooleanField(serializer).getClass().getSimpleName()).isEqualTo("ObjectBooleanField");
        assertThat(read.enabled()).isTrue();
        assertThat(copy.enabled()).isTrue();
    }

    @Test
    void readsFalseBooleanValuesDuringRoundTrip() {
        Kryo kryo = newKryo();
        FieldSerializer<BooleanFieldSubject> serializer = new FieldSerializer<>(kryo, BooleanFieldSubject.class);
        BooleanFieldSubject original = new BooleanFieldSubject(false);

        BooleanFieldSubject read = roundTrip(kryo, serializer, original, BooleanFieldSubject.class);
        BooleanFieldSubject copy = serializer.copy(kryo, original);

        assertThat(objectBooleanField(serializer).getClass().getSimpleName()).isEqualTo("ObjectBooleanField");
        assertThat(read.enabled()).isFalse();
        assertThat(copy.enabled()).isFalse();
    }

    @Test
    void exposesPrivateBooleanValueThroughObjectBooleanFieldAccessor() throws Exception {
        Kryo kryo = newKryo();
        FieldSerializer<BooleanFieldSubject> serializer = new FieldSerializer<>(kryo, BooleanFieldSubject.class);
        FieldSerializer.CachedField<?> cachedField = objectBooleanField(serializer);
        BooleanFieldSubject subject = new BooleanFieldSubject(true);

        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);

        assertThat(getField.invoke(cachedField, subject)).isEqualTo(subject.enabled());
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

    private static FieldSerializer.CachedField<?> objectBooleanField(FieldSerializer<BooleanFieldSubject> serializer) {
        FieldSerializer.CachedField<?> cachedField = serializer.getField("enabled");
        assertThat(cachedField.getClass().getSimpleName()).isEqualTo("ObjectBooleanField");
        return cachedField;
    }

    public static class BooleanFieldSubject {
        private boolean enabled;

        public BooleanFieldSubject() {
        }

        BooleanFieldSubject(boolean enabled) {
            this.enabled = enabled;
        }

        boolean enabled() {
            return enabled;
        }
    }
}
