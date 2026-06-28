/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMapUnsafe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapUnsafeAnonymous1Test {

    @Test
    void storesAndUpdatesEntriesThroughConcurrentMutableMapApi() {
        ConcurrentMutableMap<String, Integer> map = ConcurrentHashMapUnsafe.newMap();

        Integer absentValue = map.putIfAbsent("alpha", 1);
        Integer existingValue = map.putIfAbsent("alpha", 2);
        Integer computedValue = map.getIfAbsentPut("beta", () -> 3);
        Integer updatedValue = map.updateValue("alpha", () -> 0, value -> value + 4);

        assertThat(absentValue).isNull();
        assertThat(existingValue).isEqualTo(1);
        assertThat(computedValue).isEqualTo(3);
        assertThat(updatedValue).isEqualTo(5);
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.get("alpha")).isEqualTo(5);
        assertThat(map.get("beta")).isEqualTo(3);
    }
}
