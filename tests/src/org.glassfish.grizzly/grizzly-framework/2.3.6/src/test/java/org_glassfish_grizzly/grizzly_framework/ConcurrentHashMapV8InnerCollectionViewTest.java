/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.utils.DataStructures;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapV8InnerCollectionViewTest {
    @Test
    void createsTypedArrayForMapKeyCollectionView() {
        ConcurrentMap<String, Integer> map = DataStructures.getConcurrentMap();
        map.put("alpha", 1);
        map.put("bravo", 2);

        Set<String> keys = map.keySet();
        String[] keyArray = keys.toArray(new String[0]);

        assertThat(map.getClass().getName()).isEqualTo("org.glassfish.grizzly.utils.ConcurrentHashMapV8");
        assertThat(keys.getClass().getName())
                .isEqualTo("org.glassfish.grizzly.utils.ConcurrentHashMapV8$KeySetView");
        assertThat(keyArray)
                .containsExactlyInAnyOrder("alpha", "bravo");
    }
}
