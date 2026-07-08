/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.hsqldb.lib.InOutUtil;
import org.junit.jupiter.api.Test;

public class InOutUtilTest {
    @Test
    void serializesAndDeserializesSerializableValue() throws Exception {
        String value = "hsqldb serialization";

        byte[] serialized = InOutUtil.serialize(value);
        String deserialized = (String) InOutUtil.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isEqualTo(value);
    }

    @Test
    void deserializesSerializableCollection() throws Exception {
        ArrayList<String> values = new ArrayList<>();
        values.add("first");
        values.add("second");

        byte[] serialized = InOutUtil.serialize(values);
        Object deserialized = InOutUtil.deserialize(serialized);

        assertThat(deserialized).isEqualTo(values);
    }
}
