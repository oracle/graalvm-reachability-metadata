/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapV8InnerCollectionViewTest {
    @Test
    void typedToArrayAllocatesArrayForSmallerInputArray() {
        ConcurrentHashMapV8<String, Integer> map = new ConcurrentHashMapV8<String, Integer>();
        map.put("first", 1);
        map.put("second", 2);
        map.put("third", 3);

        String[] keys = map.keySet().toArray(new String[0]);

        assertThat(keys)
                .containsExactlyInAnyOrder("first", "second", "third")
                .isExactlyInstanceOf(String[].class);
    }
}
