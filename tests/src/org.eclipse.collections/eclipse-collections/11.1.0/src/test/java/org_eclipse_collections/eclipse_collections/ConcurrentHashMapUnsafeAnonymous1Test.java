/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMapUnsafe;
import org.junit.jupiter.api.Test;

public class ConcurrentHashMapUnsafeAnonymous1Test {
    @Test
    void newMapInitializesUnsafeBackedStorageAndSupportsConcurrentMapOperations() {
        final ConcurrentMutableMap<String, Integer> map = ConcurrentHashMapUnsafe.newMap();

        assertThat(map.put("alpha", 1)).isNull();
        assertThat(map.putIfAbsent("alpha", 10)).isEqualTo(1);
        assertThat(map.getIfAbsentPut("bravo", () -> 2)).isEqualTo(2);
        assertThat(map.updateValue("alpha", () -> 0, value -> value + 1)).isEqualTo(2);

        assertThat(map.get("alpha")).isEqualTo(2);
        assertThat(map.get("bravo")).isEqualTo(2);
    }
}
