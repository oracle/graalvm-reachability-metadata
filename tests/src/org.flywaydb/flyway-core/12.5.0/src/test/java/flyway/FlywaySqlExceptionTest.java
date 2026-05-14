/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void reportsInteractiveAuthenticationDriverFailures() {
        final Flyway flyway = Flyway.configure()
                .dataSource(new InteractiveAuthenticationFailureDataSource())
                .connectRetries(0)
                .load();

        assertThatThrownBy(flyway::info)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("MSAL4J")
                .hasMessageContaining("interactive authentication");
    }

    private static final class InteractiveAuthenticationFailureDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw interactiveAuthenticationFailure();
        }

        @Override
        public Connection getConnection(final String username, final String password) throws SQLException {
            throw interactiveAuthenticationFailure();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(final int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }

        private SQLException interactiveAuthenticationFailure() {
            return new SQLException("Unable to load MSAL4J classes for interactive authentication", (String) null);
        }
    }
}
