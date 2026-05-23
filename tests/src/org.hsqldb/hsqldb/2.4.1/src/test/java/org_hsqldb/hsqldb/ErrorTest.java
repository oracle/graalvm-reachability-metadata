/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.hsqldb.HsqlException;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

public class ErrorTest {
    @Test
    void getCodeFindsVendorCodeForSqlState() {
        int code = Error.getCode("42501");

        assertThat(code).isEqualTo(ErrorCode.X_42501);
    }

    @Test
    void userDefinedErrorUsesSqlStateSpecificVendorCode() {
        HsqlException exception = Error.error("permission denied", "42501");

        assertThat(exception.getMessage()).isEqualTo("permission denied");
        assertThat(exception.getSQLState()).isEqualTo("42501");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.X_42501);
    }

    @Test
    void sqlSignalUsesSqlStateSpecificVendorCode() throws Exception {
        try (Connection connection = openConnection("error_signal")) {
            createSignalProcedure(connection);

            assertThatExceptionOfType(SQLException.class)
                    .isThrownBy(() -> executeStatement(connection, "CALL SIGNAL_ACCESS_DENIED()"))
                    .satisfies(exception -> {
                        assertThat(exception.getSQLState()).isEqualTo("42501");
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.X_42501);
                    });
        }
    }

    private static void createSignalProcedure(Connection connection) throws Exception {
        executeStatement(connection, """
                CREATE PROCEDURE SIGNAL_ACCESS_DENIED()
                MODIFIES SQL DATA
                BEGIN ATOMIC
                  SIGNAL SQLSTATE '42501';
                END
                """);
    }

    private static void executeStatement(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection openConnection(String databaseName) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + databaseName + "_" + Long.toUnsignedString(System.nanoTime())
                + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }
}
