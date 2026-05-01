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

import net.sourceforge.htmlunit.corejs.javascript.ObjToIntMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjToIntMapTest {
    @Test
    void serializationRoundTripPreservesEntriesAndIgnoresRemovedKeys() throws Exception {
        ObjToIntMap map = new ObjToIntMap();
        map.put("alpha", 11);
        map.put("beta", 22);
        map.put("gamma", 33);
        map.remove("beta");
        map.put("delta", 44);

        ObjToIntMap copy = serializeAndDeserialize(map);

        assertThat(copy).isNotSameAs(map);
        assertThat(copy.size()).isEqualTo(3);
        assertThat(copy.get("alpha", -1)).isEqualTo(11);
        assertThat(copy.get("gamma", -1)).isEqualTo(33);
        assertThat(copy.get("delta", -1)).isEqualTo(44);
        assertThat(copy.has("beta")).isFalse();
        assertThat(copy.get("beta", -1)).isEqualTo(-1);
        assertThat(copy.getKeys()).containsExactlyInAnyOrder("alpha", "gamma", "delta");
    }

    private static ObjToIntMap serializeAndDeserialize(ObjToIntMap map) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(ObjToIntMap.class);
            return (ObjToIntMap) value;
        }
    }
}
