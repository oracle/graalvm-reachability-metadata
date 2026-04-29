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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.TreeMapSerializer;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerTreeMapSerializerTest {
    @Test
    void readsTreeMapSubclassUsingPublicComparatorConstructor() {
        Kryo kryo = newKryo();
        TreeMapSerializer serializer = new TreeMapSerializer();
        ConstructorTrackingTreeMap original = new ConstructorTrackingTreeMap(null);
        original.put("gamma", 3);
        original.put("alpha", 1);
        original.put("beta", 2);

        Output output = new Output(128, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        Map read = serializer.read(kryo, input, constructorTrackingTreeMapType());

        assertThat(read).isInstanceOf(ConstructorTrackingTreeMap.class);
        assertThat(read).containsExactlyEntriesOf(original);
        assertThat(((ConstructorTrackingTreeMap) read).createdWithComparatorConstructor).isTrue();
    }

    @Test
    void copiesTreeMapSubclassUsingPublicComparatorConstructor() {
        Kryo kryo = newKryo();
        TreeMapSerializer serializer = new TreeMapSerializer();
        ConstructorTrackingTreeMap original = new ConstructorTrackingTreeMap(null);
        original.put("north", 1);
        original.put("south", 2);

        Map copy = serializer.copy(kryo, original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy).isInstanceOf(ConstructorTrackingTreeMap.class);
        assertThat(copy).containsExactlyEntriesOf(original);
        assertThat(((ConstructorTrackingTreeMap) copy).createdWithComparatorConstructor).isTrue();
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map> constructorTrackingTreeMapType() {
        return (Class<Map>) (Class<?>) ConstructorTrackingTreeMap.class;
    }

    public static class ConstructorTrackingTreeMap extends TreeMap<String, Integer> {
        private final boolean createdWithComparatorConstructor;

        public ConstructorTrackingTreeMap(Comparator<? super String> comparator) {
            super(comparator);
            createdWithComparatorConstructor = true;
        }
    }
}
