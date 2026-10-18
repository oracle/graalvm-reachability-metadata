/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.filter.config.ConfigFilter;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigFilterTest {
    @Test
    void loadsConfigurationFromClasspathForms() {
        ConfigFilter filter = new ConfigFilter();
        Properties prefixed = filter.loadConfig("classpath:META-INF/druid-filter.properties");
        Properties fallback = filter.loadConfig("META-INF/druid-filter.properties");

        assertEquals("com.alibaba.druid.filter.stat.StatFilter", prefixed.getProperty("druid.filters.stat"));
        assertEquals(prefixed.getProperty("druid.filters.stat"), fallback.getProperty("druid.filters.stat"));
    }
}
