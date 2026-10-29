/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.common.packagescan.util.AbstractObjectUtils;
import org.junit.jupiter.api.Test;

public class AbstractObjectUtilsTest {
    @Test
    void appendsTypedArraysAndBoxesPrimitives() {
        String[] result = AbstractObjectUtils.addObjectToArray(new String[] {"config"}, "naming");
        assertArrayEquals(new String[] {"config", "naming"}, result);
        assertEquals(String[].class, result.getClass());
        assertArrayEquals(new Integer[] {4, 7}, AbstractObjectUtils.toObjectArray(new int[] {4, 7}));
        assertArrayEquals(new String[] {"first"}, AbstractObjectUtils.addObjectToArray(null, "first"));
    }
}
