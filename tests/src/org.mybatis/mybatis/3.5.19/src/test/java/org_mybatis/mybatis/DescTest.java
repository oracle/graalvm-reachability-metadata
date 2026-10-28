/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.javassist.runtime.Desc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DescTest {
    @Test
    void resolvesMethodDescriptorsWithDefaultAndContextClassLoaders() {
        assertArrayEquals(new Class<?>[]{String.class, int[].class}, Desc.getParams("(Ljava/lang/String;[I)V"));
        Desc.setUseContextClassLoaderLocally();
        try {
            assertEquals(String[].class, Desc.getType("[Ljava/lang/String;"));
        } finally {
            Desc.resetUseContextClassLoaderLocally();
        }
    }
}
