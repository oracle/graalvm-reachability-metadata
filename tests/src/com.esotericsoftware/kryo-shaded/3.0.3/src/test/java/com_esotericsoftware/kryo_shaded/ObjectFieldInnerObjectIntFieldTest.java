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
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectIntFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateIntFieldWithObjectCachedField() {
        Kryo kryo = newKryo();
        FieldSerializer<IntFieldSubject> serializer = new FieldSerializer<>(kryo, IntFieldSubject.class);
        IntFieldSubject original = new IntFieldSubject(-123456);

        IntFieldSubject read = roundTrip(kryo, serializer, original, IntFieldSubject.class);
        IntFieldSubject copy = serializer.copy(kryo, original);

        assertThat(serializer.getField("count").getClass().getSimpleName()).isEqualTo("ObjectIntField");
        assertThat(read.count()).isEqualTo(original.count());
        assertThat(copy.count()).isEqualTo(original.count());
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
}
