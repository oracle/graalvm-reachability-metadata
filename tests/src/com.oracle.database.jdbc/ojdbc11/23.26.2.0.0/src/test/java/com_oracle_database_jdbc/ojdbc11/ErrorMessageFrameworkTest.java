/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import oracle.jdbc.driver.DatabaseError;
import org.junit.jupiter.api.Test;

public class ErrorMessageFrameworkTest {
    @Test
    void createsLocalizedJdbcExceptions() {
        SQLException exception = DatabaseError.createSqlException(DatabaseError.EOJ_INVALID_URL);

        assertThat(exception.getMessage()).isNotBlank();
        assertThat(exception.getErrorCode()).isPositive();
    }
}
