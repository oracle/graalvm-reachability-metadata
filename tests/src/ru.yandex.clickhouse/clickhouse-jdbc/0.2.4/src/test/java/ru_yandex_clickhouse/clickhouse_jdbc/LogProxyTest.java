/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ru_yandex_clickhouse.clickhouse_jdbc;

import java.sql.Connection;
import java.util.Properties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.yandex.clickhouse.ClickHouseDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogProxyTest {
    @Test
    void delegatesConnectionOperationsWhenTraceLoggingIsEnabled() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("ru.yandex.clickhouse.util.LogProxy");
        logger.setLevel(Level.TRACE);

        Connection connection = new ClickHouseDriver().connect(
                "jdbc:clickhouse://127.0.0.1:8123/default"
                    + "?use_server_time_zone=false&use_time_zone=UTC"
                    + "&connection_timeout=10000&socket_timeout=10000",
                new Properties());
        try {
            assertFalse(connection.isClosed());
            assertEquals("default", connection.getCatalog());

            connection.setCatalog("analytics");
            assertEquals("analytics", connection.getCatalog());
        } finally {
            connection.close();
        }
        assertTrue(connection.isClosed());
    }
}
