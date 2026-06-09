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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FieldSerializerGenericsUtilTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void serializesCollectionWhoseElementTypeIsAGenericArray() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(false);

        FieldSerializer<GenericArrayCollectionHolder> serializer = new FieldSerializer<>(
                kryo,
                GenericArrayCollectionHolder.class,
                new Class[] {String.class});
        kryo.register(GenericArrayCollectionHolder.class, serializer);

        GenericArrayCollectionHolder<String> original = new GenericArrayCollectionHolder<>();
        original.values.add(new String[] {"first", "second"});
        original.values.add(new String[] {"third"});

        byte[] bytes = writeObject(kryo, original);
        GenericArrayCollectionHolder<String> restored = kryo.readObject(
                new Input(bytes), GenericArrayCollectionHolder.class);

        assertThat(restored.values).hasSize(2);
        assertThat(restored.values.get(0)).containsExactly("first", "second");
        assertThat(restored.values.get(1)).containsExactly("third");
    }

    private static byte[] writeObject(Kryo kryo, GenericArrayCollectionHolder<String> original) {
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class GenericArrayCollectionHolder<T> {
        private List<T[]> values = new ArrayList<>();

        public GenericArrayCollectionHolder() {
        }
    }
}
