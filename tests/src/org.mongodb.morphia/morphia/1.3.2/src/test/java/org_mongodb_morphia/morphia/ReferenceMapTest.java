/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import relocated.morphia.org.apache.commons.collections.ReferenceMap;

import java.io.ObjectStreamClass;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class ReferenceMapTest {
    @Test
    public void storesAndRemovesHardReferencedEntries() {
        final ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);

        map.put("morphia", "mongodb");
        map.put("driver", "legacy");

        assertThat(map)
                .containsEntry("morphia", "mongodb")
                .containsEntry("driver", "legacy")
                .hasSize(2);
        assertThat(map.remove("driver")).isEqualTo("legacy");
        assertThat(map).hasSize(1);
        assertThat(map.containsKey("morphia")).isTrue();
        assertThat(map.containsKey("driver")).isFalse();
    }

    @Test
    public void doesNotAdvertiseJavaSerializationSupport() {
        // ReferenceMap declares private serialization hooks, but the type does not expose Java serialization support.
        assertThat(Serializable.class.isAssignableFrom(ReferenceMap.class)).isFalse();
        assertThat(ObjectStreamClass.lookup(ReferenceMap.class)).isNull();
    }
}
