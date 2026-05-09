/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.twitter.chill.java.UnmodifiableListSerializer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UnmodifiableJavaCollectionSerializerTest {
    @Test
    void roundTripsUnmodifiableListThroughWrappedCollectionField() {
        Kryo kryo = new Kryo();
        UnmodifiableListSerializer serializer = new UnmodifiableListSerializer();
        List<String> mutableItems = new ArrayList<>();
        mutableItems.add("one");
        mutableItems.add("two");
        mutableItems.add("three");
        List<?> original = Collections.unmodifiableList(mutableItems);

        Output output = new Output(128, 4096);
        serializer.write(kryo, output, original);
        output.flush();

        Input input = new Input(output.toBytes());
        List<?> copy = serializer.read(kryo, input, listClass(original));

        assertThat(copy).isEqualTo(original);
        assertThat(copy).isInstanceOf(original.getClass());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> addElement(copy));
    }

    @SuppressWarnings("unchecked")
    private static Class<List<?>> listClass(List<?> list) {
        return (Class<List<?>>) list.getClass();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addElement(List<?> list) {
        ((List) list).add("four");
    }
}
