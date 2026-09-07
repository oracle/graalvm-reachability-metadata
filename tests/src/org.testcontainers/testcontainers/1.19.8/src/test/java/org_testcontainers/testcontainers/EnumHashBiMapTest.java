/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.EnumHashBiMap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumHashBiMapTest {
    @Test
    void preservesEnumKeysWhenSerialized() {
        EnumHashBiMap<Key, String> values = EnumHashBiMap.create(Key.class);
        values.put(Key.ONE, "first");
        EnumHashBiMap<Key, String> restored = SerializationUtils.roundtrip(values);

        assertThat(restored.get(Key.ONE)).isEqualTo("first");
        assertThat(restored.keyType()).isEqualTo(Key.class);
    }

    public enum Key { ONE }
}
