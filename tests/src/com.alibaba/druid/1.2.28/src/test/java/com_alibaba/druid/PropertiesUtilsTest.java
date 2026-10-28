/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.ha.PropertiesUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class PropertiesUtilsTest {
    @Test
    void discoversNamedPoolsFromClasspathConfiguration() {
        Properties properties = PropertiesUtils.loadProperties("/druid-ha-test.properties");
        assertEquals(List.of("pool.primary"), PropertiesUtils.loadNameList(properties, "pool."));
        assertEquals("jdbc:h2:mem:primary", PropertiesUtils.filterPrefix(properties, "pool.primary.")
                .getProperty("pool.primary.url"));
    }
}
