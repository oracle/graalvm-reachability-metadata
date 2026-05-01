/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import net.sourceforge.htmlunit.corejs.javascript.UintMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UintMapTest {
    @Test
    void serializationRoundTripPreservesIntegerAndObjectValues() throws Exception {
        UintMap map = new UintMap();
        map.put(1, "one");
        map.put(1, 11);
        map.put(2, "two");
        map.put(3, 33);
        map.put(4, "removed");
        map.remove(4);
        map.put(17, "seventeen");
        map.put(17, 1717);

        UintMap copy = serializeAndDeserialize(map);

        assertThat(copy).isNotSameAs(map);
        assertThat(copy.size()).isEqualTo(4);
        assertThat(copy.has(4)).isFalse();
        assertThat(copy.getObject(1)).isEqualTo("one");
        assertThat(copy.getInt(1, -1)).isEqualTo(11);
        assertThat(copy.getObject(2)).isEqualTo("two");
        assertThat(copy.getInt(2, -1)).isZero();
        assertThat(copy.getObject(3)).isNull();
        assertThat(copy.getInt(3, -1)).isEqualTo(33);
        assertThat(copy.getObject(17)).isEqualTo("seventeen");
        assertThat(copy.getInt(17, -1)).isEqualTo(1717);
        assertThat(copy.getKeys()).containsExactlyInAnyOrder(1, 2, 3, 17);
    }

    private static UintMap serializeAndDeserialize(UintMap map) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(UintMap.class);
            return (UintMap) value;
        }
    }
}
