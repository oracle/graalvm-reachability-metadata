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
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.support.CustomSQLErrorCodesTranslation;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLErrorCodes;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLErrorCodeSQLExceptionTranslatorTest {
    @Test
    void translatesUsingMessageOnlyConstructor() {
        SQLException failure = failure();
        DataAccessException translated = translate(MessageException.class, failure);
        assertThat(translated).isExactlyInstanceOf(MessageException.class).hasMessage("save: rejected");
    }

    @Test
    void translatesUsingMessageAndThrowableConstructor() {
        SQLException failure = failure();
        DataAccessException translated = translate(MessageCauseException.class, failure);
        assertThat(translated).isExactlyInstanceOf(MessageCauseException.class)
                .hasMessageStartingWith("save: rejected").hasCause(failure);
    }

    @Test
    void translatesUsingMessageAndSqlExceptionConstructor() {
        SQLException failure = failure();
        DataAccessException translated = translate(MessageSqlException.class, failure);
        assertThat(translated).isExactlyInstanceOf(MessageSqlException.class)
                .hasMessageStartingWith("save: rejected").hasCause(failure);
    }

    @Test
    void translatesUsingTaskSqlAndThrowableConstructor() {
        SQLException failure = failure();
        DataAccessException translated = translate(SqlCauseException.class, failure);
        assertThat(translated).isExactlyInstanceOf(SqlCauseException.class)
                .hasMessageStartingWith("save: INSERT INTO orders VALUES (1)").hasCause(failure);
    }

    @Test
    void translatesUsingTaskSqlAndSqlExceptionConstructor() {
        SQLException failure = failure();
        DataAccessException translated = translate(BadSqlGrammarException.class, failure);
        assertThat(translated).isExactlyInstanceOf(BadSqlGrammarException.class).hasCause(failure);
        assertThat(((BadSqlGrammarException) translated).getSql()).isEqualTo("INSERT INTO orders VALUES (1)");
        assertThat((Throwable) ((BadSqlGrammarException) translated).getSQLException()).isSameAs(failure);
        assertThat(translated.getMessage()).contains("save");
    }

    private static SQLException failure() {
        return new SQLException("rejected", "42000", 70001);
    }

    private static DataAccessException translate(Class<? extends DataAccessException> exceptionType, SQLException failure) {
        CustomSQLErrorCodesTranslation mapping = new CustomSQLErrorCodesTranslation();
        mapping.setErrorCodes("70001");
        mapping.setExceptionClass(exceptionType);
        SQLErrorCodes codes = new SQLErrorCodes();
        codes.setCustomTranslations(mapping);
        return new SQLErrorCodeSQLExceptionTranslator(codes).translate("save", "INSERT INTO orders VALUES (1)", failure);
    }

    public static class MessageException extends DataAccessException {
        public MessageException(String message) {
            super(message);
        }
    }

    public static class MessageCauseException extends DataAccessException {
        public MessageCauseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MessageSqlException extends DataAccessException {
        public MessageSqlException(String message, SQLException cause) {
            super(message, cause);
        }
    }

    public static class SqlCauseException extends DataAccessException {
        public SqlCauseException(String task, String sql, Throwable cause) {
            super(task + ": " + sql, cause);
        }
    }
}
