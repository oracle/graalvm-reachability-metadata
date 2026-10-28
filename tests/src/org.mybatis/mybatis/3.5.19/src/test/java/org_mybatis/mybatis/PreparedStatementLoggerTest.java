/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import org.apache.ibatis.logging.jdbc.PreparedStatementLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreparedStatementLoggerTest {
    @Test
    void logsBoundParametersQueriesUpdatesAndBatches() throws Exception {
        TestSupport.RecordingLog log = new TestSupport.RecordingLog();
        try (Connection connection = TestSupport.dataSource().getConnection();
                PreparedStatement statement = PreparedStatementLogger.newInstance(
                        connection.prepareStatement("SELECT CAST(? AS VARCHAR)"), log, 1)) {
            assertNotNull(statement.toString());
            statement.setString(1, "Ada");
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals("Ada", rows.getString(1));
            }
            statement.setNull(1, Types.VARCHAR);
            assertTrue(statement.execute());
            try (ResultSet rows = statement.getResultSet()) {
                rows.next();
                assertEquals(null, rows.getString(1));
            }
            assertEquals(-1, statement.getUpdateCount());
            try (PreparedStatement create = connection.prepareStatement("CREATE TABLE logged (id INT)")) {
                create.execute();
            }
            try (PreparedStatement insert = PreparedStatementLogger.newInstance(
                    connection.prepareStatement("INSERT INTO logged VALUES (?)"), log, 1)) {
                insert.setInt(1, 1);
                assertEquals(1, insert.executeUpdate());
                assertEquals(1, insert.getUpdateCount());
                insert.setInt(1, 2);
                insert.addBatch();
                assertEquals(1, insert.executeBatch()[0]);
            }
        }
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("Ada(String)")));
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("null")));
    }
}
