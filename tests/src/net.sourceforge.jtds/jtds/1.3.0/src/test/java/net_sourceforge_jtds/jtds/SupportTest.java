/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLWarning;

import net.sourceforge.jtds.jdbc.Support;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SupportTest {
    @Test
    void attachesTheOriginalCauseToAnSqlException() {
        IOException cause = new IOException("Connection closed by peer");
        SQLException exception = new SQLException("Cannot read response", "08S01", 17);

        assertSame(exception, Support.linkException(exception, cause));
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getSQLState()).isEqualTo("08S01");
        assertThat(exception.getErrorCode()).isEqualTo(17);
        assertThat(exception.getMessage()).isEqualTo("Cannot read response");
    }

    @Test
    void attachesCausesToWarningsAndGeneralExceptions() {
        IOException cause = new IOException("Truncated response");
        SQLWarning warning = new SQLWarning("Response warning", "01000");
        Exception exception = new Exception("Operation warning");

        assertSame(warning, Support.linkException(warning, cause));
        assertThat(warning.getCause()).isSameAs(cause);
        assertThat(warning.getSQLState()).isEqualTo("01000");
        assertThat(Support.linkException(exception, cause)).isSameAs(exception);
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
