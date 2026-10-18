/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.wall.spi.WallVisitorUtils;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class WallVisitorUtilsTest {
    @Test
    void loadsBundledWallRules() {
        Set<String> names = new HashSet<>();
        WallVisitorUtils.loadResource(names, "META-INF/druid/wall/mysql/deny-function.txt");
        assertFalse(names.isEmpty());
    }
}
