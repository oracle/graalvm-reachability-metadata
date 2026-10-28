/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.JdbcUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.Driver;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(120)
public class JdbcUtilsTest {
    @Test
    void constructsDriverUsingExplicitContextAndFallbackLoaders() throws Exception {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        try {
            verifyDriver(JdbcUtils.createDriver(context, "org.h2.Driver"));
            verifyDriver(JdbcUtils.createDriver("org.h2.Driver"));
            Thread.currentThread().setContextClassLoader(null);
            verifyDriver(JdbcUtils.createDriver("org.h2.Driver"));
        } finally {
            Thread.currentThread().setContextClassLoader(context);
        }
        assertEquals("com.alibaba.druid.mock.MockDriver", JdbcUtils.getDriverClassName("jdbc:fake:config"));
    }

    private static void verifyDriver(Driver driver) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "sa");
        try (Connection connection = driver.connect("jdbc:h2:mem:jdbc_utils", properties)) {
            assertNotNull(connection);
            assertEquals(17, TestDataSources.queryInt(connection, "SELECT 17"));
        }
    }
}
