/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.JdbcUtils;
import java.sql.Driver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcUtilsTest {
    @Test
    void createsJdbcDriverThroughAvailableClassLoadingPaths() throws Exception {
        Driver contextual = JdbcUtils.createDriver(null, "org.h2.Driver");
        Driver explicit = JdbcUtils.createDriver(org.h2.Driver.class.getClassLoader(), "org.h2.Driver");
        assertEquals("org.h2.Driver", contextual.getClass().getName());
        assertEquals("org.h2.Driver", explicit.getClass().getName());

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            Driver fallback = JdbcUtils.createDriver(null, "org.h2.Driver");
            assertEquals("org.h2.Driver", fallback.getClass().getName());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
