/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.BiMap;
import org.testcontainers.shaded.com.google.common.collect.EnumHashBiMap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractBiMapInnerInverseTest {
    @Test
    @SuppressWarnings("unchecked")
    void preservesTheInverseViewWhenSerialized() {
        EnumHashBiMap<Key, String> values = EnumHashBiMap.create(Key.class);
        values.put(Key.ONE, "first");
        BiMap<String, Key> restored = (BiMap<String, Key>) SerializationUtils.roundtrip(
            (java.io.Serializable) values.inverse()
        );

        assertThat(restored.get("first")).isEqualTo(Key.ONE);
        assertThat(restored.inverse().get(Key.ONE)).isEqualTo("first");
    }

    public enum Key { ONE }
}
