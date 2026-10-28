/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.ibatis.logging.jdbc.StatementLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatementLoggerTest {
    @Test
    void delegatesStatementOperationsAndLogsSql() throws Exception {
        TestSupport.RecordingLog log = new TestSupport.RecordingLog();
        try (Connection connection = TestSupport.dataSource().getConnection();
                Statement statement = StatementLogger.newInstance(connection.createStatement(), log, 1)) {
            assertNotNull(statement.toString());
            assertTrue(statement.execute("SELECT 5"));
            try (ResultSet rows = statement.getResultSet()) {
                rows.next();
                assertEquals(5, rows.getInt(1));
            }
            try (ResultSet rows = statement.executeQuery("SELECT 8")) {
                rows.next();
                assertEquals(8, rows.getInt(1));
            }
            assertEquals(-1, statement.getUpdateCount());
        }
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("SELECT 5")));
    }
}
