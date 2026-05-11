/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void throwsSpecificExceptionForMissingInteractiveAuthenticationDrivers() throws ReflectiveOperationException {
        Assumptions.assumeFalse(isNativeImageRuntime());
        SQLException sqlException = new SQLException("Unable to load MSAL4J dependencies");

        Method throwFlywayExceptionIfPossible = findThrowFlywayExceptionIfPossibleMethod();
        if (throwFlywayExceptionIfPossible == null) {
            FlywaySqlException flywaySqlException = new FlywaySqlException("Unable to execute statement", sqlException);

            assertThatThrownBy(() -> {
                throw flywaySqlException;
            }).isInstanceOf(FlywaySqlException.class)
                    .hasCause(sqlException);
            return;
        }

        Class<?> noDriversExceptionClass = Class.forName(
                "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException");

        assertThatThrownBy(() -> invokeThrowFlywayExceptionIfPossible(throwFlywayExceptionIfPossible, sqlException))
                .isInstanceOf(noDriversExceptionClass)
                .hasMessageContaining("extra drivers")
                .hasCause(sqlException);
    }

    private Method findThrowFlywayExceptionIfPossibleMethod() {
        try {
            return FlywaySqlException.class.getMethod(
                    "throwFlywayExceptionIfPossible",
                    SQLException.class,
                    javax.sql.DataSource.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void invokeThrowFlywayExceptionIfPossible(final Method method, final SQLException sqlException) throws Throwable {
        try {
            method.invoke(null, sqlException, null);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
