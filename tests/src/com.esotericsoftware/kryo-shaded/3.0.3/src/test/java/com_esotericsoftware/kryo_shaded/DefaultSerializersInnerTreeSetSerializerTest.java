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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.TreeSetSerializer;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerTreeSetSerializerTest {
    @Test
    void readsTreeSetSubclassUsingPublicComparatorConstructor() {
        Kryo kryo = newKryo();
        TreeSetSerializer serializer = new TreeSetSerializer();
        ConstructorTrackingTreeSet original = new ConstructorTrackingTreeSet(null);
        original.add("gamma");
        original.add("alpha");
        original.add("beta");

        Output output = new Output(128, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        Collection read = serializer.read(kryo, input, constructorTrackingTreeSetType());

        assertThat(read).isInstanceOf(ConstructorTrackingTreeSet.class);
        assertThat(read).containsExactlyElementsOf(original);
        assertThat(((ConstructorTrackingTreeSet) read).createdWithComparatorConstructor).isTrue();
    }

    @Test
    void copiesTreeSetSubclassUsingPublicComparatorConstructor() {
        Kryo kryo = newKryo();
        TreeSetSerializer serializer = new TreeSetSerializer();
        ConstructorTrackingTreeSet original = new ConstructorTrackingTreeSet(null);
        original.add("north");
        original.add("south");

        Collection copy = serializer.copy(kryo, original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy).isInstanceOf(ConstructorTrackingTreeSet.class);
        assertThat(copy).containsExactlyElementsOf(original);
        assertThat(((ConstructorTrackingTreeSet) copy).createdWithComparatorConstructor).isTrue();
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    @SuppressWarnings("unchecked")
    private static Class<Collection> constructorTrackingTreeSetType() {
        return (Class<Collection>) (Class<?>) ConstructorTrackingTreeSet.class;
    }

    public static class ConstructorTrackingTreeSet extends TreeSet<String> {
        private final boolean createdWithComparatorConstructor;

        public ConstructorTrackingTreeSet(Comparator<? super String> comparator) {
            super(comparator);
            createdWithComparatorConstructor = true;
        }
    }
}
