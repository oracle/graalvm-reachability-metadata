/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import relocated.morphia.org.apache.commons.collections.ReferenceMap;

public class ReferenceMapTest {
    @Test
    @SuppressWarnings("unchecked")
    void managesEntriesUsingHardReferences() {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);

        assertThat(map.put("alpha", "one")).isNull();
        assertThat(map.put("beta", "two")).isNull();
        assertThat(map.put("alpha", "uno")).isEqualTo("one");

        assertThat(map).containsEntry("alpha", "uno")
                .containsEntry("beta", "two")
                .hasSize(2);
        assertThat(map.keySet()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(map.values()).containsExactlyInAnyOrder("uno", "two");
        assertThat(map.remove("beta")).isEqualTo("two");
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get("alpha")).isEqualTo("uno");
        assertThat(map.containsKey("alpha")).isTrue();

        map.clear();

        assertThat(map).isEmpty();
    }
}
