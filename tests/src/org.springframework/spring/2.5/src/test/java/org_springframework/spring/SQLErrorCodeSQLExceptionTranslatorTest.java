/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.CustomSQLErrorCodesTranslation;
import org.springframework.jdbc.support.SQLErrorCodes;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

import static org.junit.jupiter.api.Assertions.*;

public class SQLErrorCodeSQLExceptionTranslatorTest {
    public static class MessageException extends DataAccessException {
        public MessageException(String message) {
            super(message);
        }
    }

    public static class CauseException extends DataAccessException {
        public CauseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class SqlCauseException extends DataAccessException {
        public SqlCauseException(String message, SQLException cause) {
            super(message, cause);
        }
    }

    public static class QueryCauseException extends DataAccessException {
        private final String sql;

        public QueryCauseException(String message, String sql, Throwable cause) {
            super(message, cause);
            this.sql = sql;
        }
    }

    public static class QuerySqlCauseException extends DataAccessException {
        private final String sql;

        public QuerySqlCauseException(String message, String sql, SQLException cause) {
            super(message, cause);
            this.sql = sql;
        }
    }

    @Test
    void honorsAllSupportedCustomExceptionConstructors() {
        Class<?>[] types = {MessageException.class, CauseException.class, SqlCauseException.class,
                QueryCauseException.class, QuerySqlCauseException.class};
        for (Class<?> type : types) {
            CustomSQLErrorCodesTranslation mapping = new CustomSQLErrorCodesTranslation();
            mapping.setErrorCodes(new String[]{"910"});
            mapping.setExceptionClass(type);
            SQLErrorCodes codes = new SQLErrorCodes();
            codes.setCustomTranslations(new CustomSQLErrorCodesTranslation[]{mapping});
            SQLErrorCodeSQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(codes);
            translator.setSubclassTranslator(null);
            SQLException failure = new SQLException("vendor failure", "ZX000", 910);
            DataAccessException translated = translator.translate("read", "select value", failure);
            assertSame(type, translated.getClass());
            assertTrue(translated.getMessage().contains("read"));
            if (type != MessageException.class) {
                assertSame(failure, translated.getCause());
            }
            if (translated instanceof QueryCauseException query) {
                assertEquals("select value", query.sql);
            }
            if (translated instanceof QuerySqlCauseException query) {
                assertEquals("select value", query.sql);
            }
        }
    }
}
