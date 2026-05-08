/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.collection.IntObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntObjectHashMapTest {
    @Test
    void valuesCreatesTypedArrayWithMapContents() {
        IntObjectHashMap<String> map = new IntObjectHashMap<String>();
        map.put(1, "one");
        map.put(12, "twelve");

        String[] values = map.values(String.class);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactlyInAnyOrder("one", "twelve");
    }
}
