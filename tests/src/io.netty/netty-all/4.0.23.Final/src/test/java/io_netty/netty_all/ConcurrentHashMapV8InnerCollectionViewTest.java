/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.util.Arrays;
import java.util.HashSet;

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConcurrentHashMapV8InnerCollectionViewTest {
    @Test
    void typedToArrayAllocatesMatchingArrayWhenInputIsTooSmall() {
        ConcurrentHashMapV8<String, String> map = new ConcurrentHashMapV8<String, String>();
        map.put("first", "alpha");
        map.put("second", "bravo");

        String[] supplied = new String[0];
        String[] keys = map.keySet().toArray(supplied);

        Assertions.assertNotSame(supplied, keys);
        Assertions.assertEquals(String[].class, keys.getClass());
        Assertions.assertEquals(map.size(), keys.length);
        Assertions.assertEquals(new HashSet<String>(Arrays.asList("first", "second")),
                new HashSet<String>(Arrays.asList(keys)));
    }
}
