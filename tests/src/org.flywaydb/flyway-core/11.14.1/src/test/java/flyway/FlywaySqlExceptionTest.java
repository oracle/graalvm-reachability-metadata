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
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void throwsSpecificExceptionForSqlServerUntrustedCertificate() {
        SQLException sqlException = new SQLException(
                "The certificate chain was issued by an authority that is not trusted.",
                "08S01",
                new CertPathBuilderException("unable to find valid certification path"));

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, null))
                .isInstanceOfSatisfying(FlywaySqlServerUntrustedCertificateSqlException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(sqlException);
                    assertThat(exception.getSqlState()).isEqualTo("08S01");
                    assertThat(exception.getMessage()).contains("The server certificate is not trusted");
                });
    }
}
