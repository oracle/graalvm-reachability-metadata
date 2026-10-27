/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.internal.util.collections.BoundedConcurrentHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BoundedConcurrentHashMapTest {

    @Test
    public void preservesAnEmptyMapAcrossSerialization() {
        BoundedConcurrentHashMap<String, String> map = new BoundedConcurrentHashMap<>(8, 2);

        BoundedConcurrentHashMap<?, ?> copy =
                (BoundedConcurrentHashMap<?, ?>) SerializationHelper.clone(map);

        assertThat(copy).isEmpty();
    }

    @Test
    public void serializesMapEntries() {
        BoundedConcurrentHashMap<String, String> map = new BoundedConcurrentHashMap<>(8, 2);
        map.put("framework", "hibernate");

        byte[] serialized = SerializationHelper.serialize(map);

        assertThat(serialized).isNotEmpty();
    }
}
