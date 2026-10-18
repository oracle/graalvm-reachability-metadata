/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.ha.PropertiesUtils;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertiesUtilsTest {
    @Test
    void fallsBackToBundledClasspathResource() {
        Properties properties = PropertiesUtils.loadProperties("/META-INF/druid-filter.properties");
        assertEquals("com.alibaba.druid.filter.stat.StatFilter", properties.getProperty("druid.filters.stat"));
    }
}
