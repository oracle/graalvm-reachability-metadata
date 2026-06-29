/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.HsqlException;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

public class ErrorTest {
    @Test
    void looksUpSqlStateCodeByInspectingErrorCodeConstants() {
        int code = Error.getCode("45000");

        assertThat(code).isEqualTo(ErrorCode.X_45000);
    }

    @Test
    void resolvesSqlStateThroughErrorFactory() {
        HsqlException exception = Error.error("planned user signal", "45000");

        assertThat(exception.getSQLState()).isEqualTo("45000");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.X_45000);
        assertThat(exception.getMessage()).isEqualTo("planned user signal");
    }

    @Test
    void resolvesSignalSqlStateToVendorErrorCode() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE PROCEDURE signal_user_error()
                    MODIFIES SQL DATA
                    BEGIN ATOMIC
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'planned user signal';
                    END
                    """);

            assertThatThrownBy(() -> statement.execute("CALL signal_user_error()"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(throwable -> {
                        SQLException exception = (SQLException) throwable;

                        assertThat(exception.getSQLState()).isEqualTo("45000");
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.X_45000);
                        assertThat(exception.getMessage()).contains("planned user signal");
                    });
        }
    }

    private static Connection openConnection() throws SQLException {
        return dataSource(randomDatabaseName()).getConnection();
    }

    private static JDBCDataSource dataSource(String databaseName) {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + databaseName + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource;
    }

    private static String randomDatabaseName() {
        return "ErrorTest" + UUID.randomUUID().toString().replace("-", "");
    }
}
