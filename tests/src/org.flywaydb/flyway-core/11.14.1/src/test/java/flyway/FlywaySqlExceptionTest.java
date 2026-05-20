/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.io.PrintWriter;
import java.security.cert.CertPathBuilderException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlUnableToConnectToDbException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void checksEverySpecificSqlExceptionMatcherBeforeReportingUnrecognizedConnectionFailures() {
        SQLException sqlException = new SQLException("ordinary database failure", "42000");

        assertThatThrownBy(() -> migrateWithFailingDataSource(sqlException))
                .isInstanceOf(FlywaySqlUnableToConnectToDbException.class)
                .hasCause(sqlException)
                .hasMessageContaining("Unable to obtain connection from database");
    }

    @Test
    void throwsSpecificExceptionForUntrustedSqlServerCertificateFailures() {
        SQLException sqlException = new SQLException(
                "certificate validation failed",
                "08S01",
                new CertPathBuilderException("unable to build certification path"));

        assertThatThrownBy(() -> migrateWithFailingDataSource(sqlException))
                .isInstanceOf(FlywaySqlServerUntrustedCertificateSqlException.class)
                .hasCause(sqlException)
                .hasMessageContaining("The server certificate is not trusted");
    }

    @Test
    void throwsSpecificExceptionForMissingIntegratedAuthenticationConfiguration() {
        SQLException sqlException = new SQLException(
                "This driver is not configured for integrated authentication",
                "08S01");

        assertThatThrownBy(() -> migrateWithFailingDataSource(sqlException))
                .isInstanceOf(FlywaySqlNoIntegratedAuthException.class)
                .hasCause(sqlException)
                .hasMessageContaining("To setup integrated authentication");
    }

    @Test
    void throwsSpecificExceptionForMissingInteractiveAuthenticationDrivers() {
        SQLException sqlException = new SQLException("MSAL4J classes are not available");

        assertThatThrownBy(() -> migrateWithFailingDataSource(sqlException))
                .isInstanceOf(FlywaySqlNoDriversForInteractiveAuthException.class)
                .hasCause(sqlException)
                .hasMessageContaining("interactive authentication");
    }

    private void migrateWithFailingDataSource(SQLException sqlException) {
        Flyway.configure()
                .dataSource(new FailingDataSource(sqlException))
                .resourceProvider(new FixedResourceProvider())
                .connectRetries(0)
                .load()
                .migrate();
    }

    private static final class FailingDataSource implements DataSource {
        private final SQLException sqlException;
        private PrintWriter logWriter;
        private int loginTimeout;

        private FailingDataSource(SQLException sqlException) {
            this.sqlException = sqlException;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw sqlException;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw sqlException;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
