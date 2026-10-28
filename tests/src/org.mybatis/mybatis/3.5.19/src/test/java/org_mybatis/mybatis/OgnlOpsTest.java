/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.ognl.OgnlOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class OgnlOpsTest {
    @Test
    void convertsArraysCollectionsAndScalarValues() {
        assertArrayEquals(new int[]{2, 4}, (int[]) OgnlOps.convertValue(new String[]{"2", "4"}, int[].class));
        assertArrayEquals(new String[]{"a", "b"}, (String[]) OgnlOps.toArray(List.of("a", "b"), String.class));
        assertArrayEquals(new Integer[]{3}, (Integer[]) OgnlOps.toArray("3", Integer.class));
        assertArrayEquals(new Long[]{5L, 6L}, (Long[]) OgnlOps.toArray(new int[]{5, 6}, Long.class));
    }
}
