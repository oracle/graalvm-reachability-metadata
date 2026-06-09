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
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerTreeSetSerializerTest {
    @Test
    void readsTreeSetSubclassWithPublicComparatorConstructor() {
        Kryo kryo = new Kryo();
        kryo.register(ReverseStringComparator.class);
        kryo.register(StringTreeSet.class, new DefaultSerializers.TreeSetSerializer());

        StringTreeSet original = new StringTreeSet(new ReverseStringComparator());
        original.add("alpha");
        original.add("beta");
        original.add("gamma");

        Output output = new Output(128, -1);
        kryo.writeObject(output, original);
        output.close();

        StringTreeSet restored = kryo.readObject(new Input(output.toBytes()), StringTreeSet.class);

        assertThat(restored).isInstanceOf(StringTreeSet.class);
        assertThat(restored.isConstructedWithComparator()).isTrue();
        assertThat(restored.comparator()).isInstanceOf(ReverseStringComparator.class);
        assertThat(restored).containsExactly("gamma", "beta", "alpha");
    }

    public static class StringTreeSet extends TreeSet<String> {
        private final boolean constructedWithComparator;

        public StringTreeSet(Comparator<? super String> comparator) {
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
