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
    void serializesParameterizedFieldsWhoseArgumentsAreGenericArrays() {
        Kryo kryo = newKryo();
        FieldSerializer<GenericArrayContainer> serializer =
                new FieldSerializer<>(kryo, GenericArrayContainer.class, new Class[] {String.class});
        GenericArrayContainer<String> original = new GenericArrayContainer<>();
        original.values = new ArrayList<>();
        original.values.add(new String[] {"alpha", "beta"});
        original.values.add(new String[] {"gamma"});

        GenericArrayContainer read = roundTrip(kryo, serializer, original, GenericArrayContainer.class);

        assertThat(read.values).hasSize(2);
        assertThat(read.values.get(0)).isInstanceOf(String[].class);
        assertThat((String[]) read.values.get(0)).containsExactly("alpha", "beta");
        assertThat((String[]) read.values.get(1)).containsExactly("gamma");
    }

    @Test
    void copiesParameterizedFieldsWhoseArgumentsAreGenericArrays() {
        Kryo kryo = newKryo();
        FieldSerializer<GenericArrayContainer> serializer =
                new FieldSerializer<>(kryo, GenericArrayContainer.class, new Class[] {Integer.class});
        GenericArrayContainer<Integer> original = new GenericArrayContainer<>();
        original.values = new ArrayList<>();
        original.values.add(new Integer[] {1, 2, 3});

        GenericArrayContainer copy = serializer.copy(kryo, original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.values).isNotSameAs(original.values);
        assertThat(copy.values).hasSize(1);
        assertThat(copy.values.get(0)).isInstanceOf(Integer[].class);
        assertThat((Integer[]) copy.values.get(0)).containsExactly(1, 2, 3);
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
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

    public static class GenericArrayContainer<T> {
        public List<T[]> values;

        public GenericArrayContainer() {
        }
    }
}
