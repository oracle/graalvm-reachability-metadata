/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.sql.SQLException;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void throwsSpecificExceptionForMissingInteractiveAuthenticationDrivers() {
        SQLException sqlException = new SQLException("Unable to load MSAL4J dependencies");

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, null))
                .isInstanceOf(FlywaySqlNoDriversForInteractiveAuthException.class)
                .hasMessageContaining("extra drivers")
                .hasCause(sqlException);
    }
}
