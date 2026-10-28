/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.support.logging.Resources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(120)
public class ResourcesTest {
    @Test
    void resolvesJdbcUnwrapTypeWithAndWithoutContextLoader() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (DruidDataSource dataSource = TestDataSources.create("resource_loader");
             Connection connection = dataSource.getConnection()) {
            for (ClassLoader loader : new ClassLoader[]{previous, null}) {
                Thread.currentThread().setContextClassLoader(loader);
                Class<?> connectionType = Resources.classForName("org.h2.jdbc.JdbcConnection");
                Connection physical = assertInstanceOf(Connection.class, connection.unwrap(connectionType));
                assertEquals(41, TestDataSources.queryInt(physical, "SELECT 41"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
