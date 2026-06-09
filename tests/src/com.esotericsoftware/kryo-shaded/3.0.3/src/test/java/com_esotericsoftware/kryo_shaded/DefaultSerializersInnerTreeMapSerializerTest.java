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
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import java.util.Comparator;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerTreeMapSerializerTest {
    @Test
    void readsTreeMapSubclassWithPublicComparatorConstructor() {
        Kryo kryo = new Kryo();
        kryo.register(ReverseStringComparator.class);
        kryo.register(StringKeyTreeMap.class, new DefaultSerializers.TreeMapSerializer());

        StringKeyTreeMap original = new StringKeyTreeMap(new ReverseStringComparator());
        original.put("alpha", 1);
        original.put("beta", 2);
        original.put("gamma", 3);

        Output output = new Output(128, -1);
        kryo.writeObject(output, original);
        output.close();

        StringKeyTreeMap restored = kryo.readObject(new Input(output.toBytes()), StringKeyTreeMap.class);

        assertThat(restored).isInstanceOf(StringKeyTreeMap.class);
        assertThat(restored.isConstructedWithComparator()).isTrue();
        assertThat(restored.comparator()).isInstanceOf(ReverseStringComparator.class);
        assertThat(restored).containsEntry("alpha", 1).containsEntry("beta", 2).containsEntry("gamma", 3);
        assertThat(restored.keySet()).containsExactly("gamma", "beta", "alpha");
    }

    public static class StringKeyTreeMap extends TreeMap<String, Integer> {
        private final boolean constructedWithComparator;

        public StringKeyTreeMap(Comparator<? super String> comparator) {
            super(comparator);
            constructedWithComparator = true;
        }

        public boolean isConstructedWithComparator() {
            return constructedWithComparator;
        }
    }

    public static class ReverseStringComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }
}
