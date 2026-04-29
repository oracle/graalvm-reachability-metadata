/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers.ObjectArraySerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultArraySerializersInnerObjectArraySerializerTest {
    @Test
    void readsObjectArrayUsingComponentType() {
        Kryo kryo = new Kryo();
        Class<Object[]> arrayType = integerArrayType();
        ObjectArraySerializer serializer = new ObjectArraySerializer(kryo, arrayType);
        Integer[] original = new Integer[] {1, null, 3, 5, 8};

        Output output = new Output(128, -1);
        kryo.writeObject(output, original, serializer);

        Object[] restored = kryo.readObject(new Input(output.toBytes()), arrayType, serializer);

        assertThat(restored).isInstanceOf(Integer[].class);
        assertThat((Integer[]) restored).containsExactly(1, null, 3, 5, 8);
    }

    @Test
    void copiesObjectArrayUsingComponentType() {
        Kryo kryo = new Kryo();
        ObjectArraySerializer serializer = new ObjectArraySerializer(kryo, integerArrayType());
        Integer[] original = new Integer[] {13, 21, 34};

        Object[] copied = serializer.copy(kryo, original);

        assertThat(copied).isInstanceOf(Integer[].class);
        assertThat(copied).isNotSameAs(original);
        assertThat((Integer[]) copied).containsExactly(13, 21, 34);
    }

    @SuppressWarnings("unchecked")
    private static Class<Object[]> integerArrayType() {
        return (Class<Object[]>) (Class<?>) Integer[].class;
    }
}
