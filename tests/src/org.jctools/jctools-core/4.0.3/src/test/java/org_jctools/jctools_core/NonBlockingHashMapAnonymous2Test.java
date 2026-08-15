/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonBlockingHashMapAnonymous2Test {

    @Test
    void keySetConvertsEntriesToTypedArray() {
        NonBlockingHashMap<String, Integer> map = new NonBlockingHashMap<>();
        map.put("alpha", 1);
        map.put("beta", 2);

        String[] keys = map.keySet().toArray(new String[0]);

        assertThat(keys).containsExactlyInAnyOrder("alpha", "beta");
    }
}
