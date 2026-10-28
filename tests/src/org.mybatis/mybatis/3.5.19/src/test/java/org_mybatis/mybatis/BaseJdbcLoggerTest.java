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

import org.apache.ibatis.logging.jdbc.PreparedStatementLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaseJdbcLoggerTest {
    @Test
    void recognizesJdbcSetterMethodsForParameterLogging() throws Exception {
        TestSupport.RecordingLog log = new TestSupport.RecordingLog();
        try (Connection connection = TestSupport.dataSource().getConnection();
                PreparedStatement statement = PreparedStatementLogger.newInstance(
                        connection.prepareStatement("SELECT ? + ?"), log, 0)) {
            statement.setInt(1, 4);
            statement.setLong(2, 8L);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals(12, rows.getInt(1));
            }
        }
        assertTrue(log.messages.stream().anyMatch(message -> message.contains("4(Integer), 8(Long)")));
    }
}
