/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.security.cert.CertPathBuilderException;
import java.sql.SQLException;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void checksEverySpecificSqlExceptionMatcherBeforeIgnoringUnrecognizedSqlExceptions() {
        SQLException sqlException = new SQLException("ordinary database failure", "42000");

        assertThatCode(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, null))
                .doesNotThrowAnyException();
    }

    @Test
    void throwsSpecificExceptionForUntrustedSqlServerCertificateFailures() {
        SQLException sqlException = new SQLException(
                "certificate validation failed",
                "08S01",
                new CertPathBuilderException("unable to build certification path"));

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, null))
                .isInstanceOf(FlywaySqlServerUntrustedCertificateSqlException.class)
                .hasCause(sqlException);
    }

    @Test
    void throwsSpecificExceptionForMissingIntegratedAuthenticationConfiguration() {
        SQLException sqlException = new SQLException(
                "This driver is not configured for integrated authentication",
                "08S01");

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, null))
                .isInstanceOf(FlywaySqlNoIntegratedAuthException.class)
                .hasCause(sqlException);
    }

    @Test
    void throwsSpecificExceptionForMissingInteractiveAuthenticationDrivers() {
        SQLException sqlException = new SQLException("MSAL4J classes are not available");

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, null))
                .isInstanceOf(FlywaySqlNoDriversForInteractiveAuthException.class)
                .hasCause(sqlException);
    }
}
