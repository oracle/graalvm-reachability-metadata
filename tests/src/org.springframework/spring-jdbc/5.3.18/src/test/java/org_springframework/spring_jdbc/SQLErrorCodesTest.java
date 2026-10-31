/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLErrorCodes;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLErrorCodesTest {
    @Test
    void instantiatesConfiguredTranslatorAndUsesItsTranslation() {
        SQLErrorCodes codes = new SQLErrorCodes();
        codes.setCustomSqlExceptionTranslatorClass(ConstraintTranslator.class);
        assertThat(codes.getCustomSqlExceptionTranslator()).isInstanceOf(ConstraintTranslator.class);

        SQLException failure = new SQLException("constraint violation", "23000", 77);
        DataAccessException translated = new SQLErrorCodeSQLExceptionTranslator(codes)
                .translate("save order", "INSERT INTO orders VALUES (1)", failure);
        assertThat(translated).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageStartingWith("save order: INSERT INTO orders VALUES (1)")
                .hasCause(failure);

        codes.setCustomSqlExceptionTranslatorClass(null);
        assertThat(codes.getCustomSqlExceptionTranslator()).isNull();
    }

    public static class ConstraintTranslator implements SQLExceptionTranslator {
        public ConstraintTranslator() {
        }

        @Override
        public DataAccessException translate(String task, String sql, SQLException exception) {
            return new DataIntegrityViolationException(task + ": " + sql, exception);
        }
    }
}
