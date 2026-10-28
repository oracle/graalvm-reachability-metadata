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

import org.apache.ibatis.logging.jdbc.ResultSetLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResultSetLoggerTest {
    @Test
    void logsColumnNamesRowsAndTotal() throws Exception {
        TestSupport.RecordingLog log = new TestSupport.RecordingLog();
        try (Connection connection = TestSupport.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = ResultSetLogger.newInstance(
                        statement.executeQuery("SELECT 42 AS answer, CAST('data' AS BLOB) AS payload"), log, 1)) {
            assertNotNull(rows.toString());
            assertTrue(rows.next());
            assertEquals(42, rows.getInt("answer"));
            assertFalse(rows.next());
        }
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("ANSWER")));
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("<<BLOB>>")));
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("Total: 1")));
    }
}
