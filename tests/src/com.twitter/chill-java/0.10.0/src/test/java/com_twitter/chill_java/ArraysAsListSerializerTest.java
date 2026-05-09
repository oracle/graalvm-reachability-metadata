/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.twitter.chill.java.ArraysAsListSerializer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ArraysAsListSerializerTest {
    @Test
    void roundTripsListCreatedByArraysAsList() {
        Kryo kryo = new Kryo();
        ArraysAsListSerializer serializer = new ArraysAsListSerializer();
        List<String> original = Arrays.asList("red", "green", "blue");

        Output output = new Output(128, 4096);
        serializer.write(kryo, output, original);
        output.flush();

        Input input = new Input(output.toBytes());
        List<?> copy = serializer.read(kryo, input, listClass(original));

        assertThat(copy).isEqualTo(original);
        assertThat(copy).isInstanceOf(original.getClass());
    }

    @SuppressWarnings("unchecked")
    private static Class<List<?>> listClass(List<?> list) {
        return (Class<List<?>>) list.getClass();
    }
}
