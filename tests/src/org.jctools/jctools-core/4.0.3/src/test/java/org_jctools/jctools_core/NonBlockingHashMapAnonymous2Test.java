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
    void keySetCopiesKeysIntoNewTypedArray() {
        NonBlockingHashMap<String, Integer> map = new NonBlockingHashMap<>();
        map.put("first", 1);
        map.put("second", 2);
        String[] destination = new String[0];

        String[] keys = map.keySet().toArray(destination);

        assertThat(keys)
                .isNotSameAs(destination)
                .containsExactlyInAnyOrder("first", "second");
    }
}
