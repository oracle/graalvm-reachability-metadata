/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.spi.MySqlWallProvider;
import com.alibaba.druid.wall.spi.WallVisitorUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class WallVisitorUtilsTest {
    @Test
    void loadsFirewallRulesWithContextAndFallbackResourcePaths() {
        String resource = "META-INF/druid/wall/mysql/deny-function.txt";
        for (String path : new String[]{resource, "/" + resource}) {
            Set<String> functions = new HashSet<>();
            WallVisitorUtils.loadResource(functions, path);
            assertTrue(functions.contains("sleep"));
            assertTrue(functions.contains("load_file"));
        }
        MySqlWallProvider provider = new MySqlWallProvider();
        assertTrue(provider.check("SELECT id FROM orders WHERE id = 1").getViolations().isEmpty());
        WallCheckResult rejected = provider.check("SELECT id FROM orders WHERE load_file('/etc/passwd') IS NOT NULL");
        assertFalse(rejected.getViolations().isEmpty());
    }
}
