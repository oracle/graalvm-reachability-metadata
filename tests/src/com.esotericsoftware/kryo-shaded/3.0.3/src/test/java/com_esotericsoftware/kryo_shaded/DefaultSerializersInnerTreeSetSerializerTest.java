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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.TreeSetSerializer;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSerializersInnerTreeSetSerializerTest {
    @Test
    void readsTreeSetSubclassUsingPublicComparatorConstructor() {
        Kryo kryo = new Kryo();
        TreeSetSerializer serializer = new TreeSetSerializer();
        ConstructorTrackingTreeSet original = new ConstructorTrackingTreeSet(ReverseStringComparator.INSTANCE);
        original.add("bravo");
        original.add("alpha");
        original.add("charlie");

        Output output = new Output(256, -1);
        kryo.writeObject(output, original, serializer);

        ConstructorTrackingTreeSet restored = kryo.readObject(
                new Input(output.toBytes()),
                ConstructorTrackingTreeSet.class,
                serializer);

        assertThat(restored).isInstanceOf(ConstructorTrackingTreeSet.class);
        assertThat(restored.wasCreatedByComparatorConstructor()).isTrue();
        assertThat(restored.comparator()).isSameAs(ReverseStringComparator.INSTANCE);
        assertThat(restored).containsExactly("charlie", "bravo", "alpha");
    }

    public enum ReverseStringComparator implements Comparator<String> {
        INSTANCE;

        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }

    public static final class ConstructorTrackingTreeSet extends TreeSet<String> {
        private static final long serialVersionUID = 1L;

        private final boolean createdByComparatorConstructor;

        public ConstructorTrackingTreeSet(Comparator<? super String> comparator) {
            super(comparator);
            this.createdByComparatorConstructor = true;
        }

        boolean wasCreatedByComparatorConstructor() {
            return createdByComparatorConstructor;
        }
    }
}
