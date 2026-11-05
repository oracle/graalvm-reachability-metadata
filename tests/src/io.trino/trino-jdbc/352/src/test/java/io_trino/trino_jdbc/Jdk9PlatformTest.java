/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.TrinoDriver;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Jdk9PlatformTest {
    @Test
    @Timeout(120)
    void negotiatesTlsForJdbcQueriesWithVerifiedServerCertificate() throws Exception {
        try (TrinoProtocolServer server = new TrinoProtocolServer(true);
                TrinoDriver driver = new TrinoDriver();
                Connection connection = driver.connect(server.jdbcUrl(), server.properties());
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            try (ResultSet rows = statement.executeQuery("SELECT number FROM secure_example")) {
                assertTrue(rows.next());
                assertEquals(42, rows.getLong("number"));
                assertTrue(rows.next());
                assertEquals(43, rows.getLong("number"));
                assertFalse(rows.next());
            }
            assertEquals(1, server.statements.size());
        }
    }
}
