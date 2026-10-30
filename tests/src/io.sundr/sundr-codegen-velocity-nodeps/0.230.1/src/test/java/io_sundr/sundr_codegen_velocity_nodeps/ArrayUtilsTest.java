/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.sundr.deps.org.apache.commons.lang.ArrayUtils;
import org.junit.jupiter.api.Test;

public class ArrayUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        String[] input = {"first", "third"};
        assertArrayEquals(new String[]{"first", "second", "third"}, ArrayUtils.add(input, 1, "second"));
        assertArrayEquals(new String[]{"first"}, ArrayUtils.remove(input, 1));
        assertArrayEquals(new String[]{"third"}, ArrayUtils.subarray(input, 1, 3));
        assertArrayEquals(new String[0], ArrayUtils.subarray(input, 2, 2));
        assertArrayEquals(new String[]{"first", "third", "last"}, ArrayUtils.add(input, "last"));
        assertArrayEquals(new String[]{"only"}, ArrayUtils.add((String[]) null, 0, "only"));
        assertArrayEquals(new String[]{"first", "third", "last"}, ArrayUtils.addAll(input, new String[]{"last"}));
        assertArrayEquals(new Object[]{null}, ArrayUtils.add((Object[]) null, (Object) null));
        assertArrayEquals(new int[]{1}, ArrayUtils.add((int[]) null, 1));
        assertArrayEquals(new int[]{1, 2}, ArrayUtils.add(new int[]{1}, 2));
    }
}
