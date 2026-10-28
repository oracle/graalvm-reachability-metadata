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
import java.sql.Statement;

import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectionLoggerTest {
    @Test
    void logsStatementsCreatedFromConnection() throws Exception {
        TestSupport.RecordingLog log = new TestSupport.RecordingLog();
        try (Connection connection = ConnectionLogger.newInstance(TestSupport.dataSource().getConnection(), log, 1)) {
            assertNotNull(connection.toString());
            assertTrue(connection.getAutoCommit());
            try (PreparedStatement statement = connection.prepareStatement("SELECT 6");
                    ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals(6, rows.getInt(1));
            }
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT 7")) {
                rows.next();
                assertEquals(7, rows.getInt(1));
            }
        }
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("Preparing: SELECT 6")));
    }
}
