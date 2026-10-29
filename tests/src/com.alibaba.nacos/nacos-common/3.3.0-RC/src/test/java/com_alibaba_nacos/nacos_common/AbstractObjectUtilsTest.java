/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.util.AbstractObjectUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractObjectUtilsTest {
    @Test
    void appendsWithoutChangingTheOriginalArrayOrComponentType() {
        String[] original = {"alpha"};
        String[] extended = AbstractObjectUtils.addObjectToArray(original, "beta");
        assertArrayEquals(new String[]{"alpha", "beta"}, extended);
        assertArrayEquals(new String[]{"alpha"}, original);
        assertSame(String[].class, extended.getClass());
        assertArrayEquals(new String[]{"first"}, AbstractObjectUtils.addObjectToArray(null, "first"));
    }

    @Test
    void boxesPrimitiveArrays() {
        Object[] boxed = AbstractObjectUtils.toObjectArray(new int[]{7, 11});
        assertSame(Integer[].class, boxed.getClass());
        assertArrayEquals(new Integer[]{7, 11}, boxed);
        assertArrayEquals(new Boolean[]{true, false}, AbstractObjectUtils.toObjectArray(new boolean[]{true, false}));
    }
}
