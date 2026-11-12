/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.util.Properties;
import java.util.Set;

import com.codahale.metrics.MetricRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.util.PropertyElf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyElfTest {
    @Test
    public void configuresPoolSettingsAndConstructsMetricRegistryFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("poolName", "property-pool");
        properties.setProperty("maximumPoolSize", "4");
        properties.setProperty("connectionTimeout", "30000");
        properties.setProperty("autoCommit", "false");
        properties.setProperty("metricRegistry", "com.codahale.metrics.MetricRegistry");
        properties.setProperty("dataSource.URL", "jdbc:h2:mem:properties");
        Properties dataSourceProperties = new Properties();
        dataSourceProperties.setProperty("user", "sa");
        properties.put("dataSourceProperties", dataSourceProperties);

        HikariConfig config = new HikariConfig(properties);

        assertEquals("property-pool", config.getPoolName());
        assertEquals(4, config.getMaximumPoolSize());
        assertEquals(30000L, config.getConnectionTimeout());
        assertFalse(config.isAutoCommit());
        assertEquals("jdbc:h2:mem:properties", config.getDataSourceProperties().getProperty("URL"));
        assertEquals("sa", config.getDataSourceProperties().getProperty("user"));
        MetricRegistry registry = assertInstanceOf(MetricRegistry.class, config.getMetricRegistry());
        registry.counter("application.operations").inc(2);
        assertEquals(2L, registry.getCounters().get("application.operations").getCount());
    }

    @Test
    public void discoversAndReadsConfigurationBeanProperties() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("inspected-pool");
        config.setAutoCommit(false);
        config.setMaximumPoolSize(3);

        Set<String> names = PropertyElf.getPropertyNames(HikariConfig.class);

        assertTrue(names.contains("poolName"));
        assertTrue(names.contains("autoCommit"));
        assertTrue(names.contains("maximumPoolSize"));
        assertEquals("inspected-pool", PropertyElf.getProperty("poolName", config));
        assertEquals(false, PropertyElf.getProperty("autoCommit", config));
        assertEquals(3, PropertyElf.getProperty("maximumPoolSize", config));
    }
}
