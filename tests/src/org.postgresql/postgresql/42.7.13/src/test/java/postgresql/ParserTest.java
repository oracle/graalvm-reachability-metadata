/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import org.junit.jupiter.api.Test;
import org.postgresql.core.Parser;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.core.Parser}.
 */
public class ParserTest {

    @Test
    void replaceProcessingInvokesEscapedFunctionTranslation() throws Exception {
        String sql = "select {fn concat({fn ucase('pg')}, {fn lcase('JDBC')})}";

        String replacedSql = Parser.replaceProcessing(sql, true, true);

        assertThat(replacedSql).isEqualTo("select (upper('pg')|| lower('JDBC'))");
    }

    @Test
    void replaceProcessingUnwrapsEscapedFunctionSqlException() {
        String sql = "select {fn length('pg', 'jdbc')}";

        PSQLException exception = catchThrowableOfType(() -> Parser.replaceProcessing(sql, true, true), PSQLException.class);

        assertThat((Throwable) exception).isNotNull();
        assertThat(exception.getSQLState()).isEqualTo(PSQLState.SYNTAX_ERROR.getState());
    }
}
