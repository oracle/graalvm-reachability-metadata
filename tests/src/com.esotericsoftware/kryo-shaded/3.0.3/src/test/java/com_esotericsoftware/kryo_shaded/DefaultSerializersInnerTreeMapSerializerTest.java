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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.TreeMapSerializer;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSerializersInnerTreeMapSerializerTest {
    @Test
    void readsTreeMapSubclassUsingPublicComparatorConstructor() {
        Kryo kryo = new Kryo();
        TreeMapSerializer serializer = new TreeMapSerializer();
        ConstructorTrackingTreeMap original = new ConstructorTrackingTreeMap(ReverseStringComparator.INSTANCE);
        original.put("bravo", 2);
        original.put("alpha", 1);
        original.put("charlie", 3);

        Output output = new Output(256, -1);
        kryo.writeObject(output, original, serializer);

        ConstructorTrackingTreeMap restored = kryo.readObject(
                new Input(output.toBytes()),
                ConstructorTrackingTreeMap.class,
                serializer);

        assertThat(restored).isInstanceOf(ConstructorTrackingTreeMap.class);
        assertThat(restored.wasCreatedByComparatorConstructor()).isTrue();
        assertThat(restored.comparator()).isSameAs(ReverseStringComparator.INSTANCE);
        assertThat(restored.keySet()).containsExactly("charlie", "bravo", "alpha");
        assertThat(restored).containsEntry("alpha", 1).containsEntry("bravo", 2).containsEntry("charlie", 3);
    }

    public enum ReverseStringComparator implements Comparator<String> {
        INSTANCE;

        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }

    public static final class ConstructorTrackingTreeMap extends TreeMap<String, Integer> {
        private static final long serialVersionUID = 1L;

        private final boolean createdByComparatorConstructor;

        public ConstructorTrackingTreeMap(Comparator<? super String> comparator) {
            super(comparator);
            this.createdByComparatorConstructor = true;
        }

        boolean wasCreatedByComparatorConstructor() {
            return createdByComparatorConstructor;
        }
    }
}
