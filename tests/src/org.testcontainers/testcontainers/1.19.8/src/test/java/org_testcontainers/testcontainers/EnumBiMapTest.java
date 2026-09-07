/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.EnumBiMap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumBiMapTest {
    @Test
    void preservesEnumTypesAndEntriesWhenSerialized() {
        EnumBiMap<Key, Value> values = EnumBiMap.create(Key.class, Value.class);
        values.put(Key.ONE, Value.FIRST);
        EnumBiMap<Key, Value> restored = SerializationUtils.roundtrip(values);

        assertThat(restored.get(Key.ONE)).isEqualTo(Value.FIRST);
        assertThat(restored.keyType()).isEqualTo(Key.class);
    }

    public enum Key { ONE }
    public enum Value { FIRST }
}
