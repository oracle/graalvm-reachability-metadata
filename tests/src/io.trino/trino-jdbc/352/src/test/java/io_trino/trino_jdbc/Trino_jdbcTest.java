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
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Trino_jdbcTest {
    @Test
    @Timeout(120)
    void queriesPaginatedResultsThroughJdbc() throws Exception {
        try (TrinoProtocolServer server = new TrinoProtocolServer(false);
                TrinoDriver driver = new TrinoDriver();
                Connection connection = driver.connect(server.jdbcUrl(), server.properties());
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            try (ResultSet rows = statement.executeQuery("SELECT number, text, day FROM example")) {
                assertEquals(3, rows.getMetaData().getColumnCount());
                assertEquals(Types.BIGINT, rows.getMetaData().getColumnType(1));
                assertTrue(rows.next());
                assertEquals(42, rows.getLong("number"));
                assertEquals("hello", rows.getString("text"));
                assertEquals(LocalDate.of(2020, 6, 15), rows.getDate("day").toLocalDate());
                assertTrue(rows.next());
                assertEquals(43, rows.getLong(1));
                assertNull(rows.getString(2));
                assertTrue(rows.wasNull());
                assertEquals(LocalDate.of(2020, 6, 16), rows.getDate("day").toLocalDate());
                assertFalse(rows.next());
            }
            assertEquals(List.of("SELECT number, text, day FROM example"), server.statements);
            assertEquals(List.of("metadata-user"), server.users);
        }
    }
}
