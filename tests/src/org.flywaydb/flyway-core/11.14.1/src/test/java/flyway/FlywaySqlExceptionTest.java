/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.CertPathBuilderException;
import java.sql.SQLException;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class FlywaySqlExceptionTest {
    private static final String SPECIFIC_EXCEPTION_CLASS_NAME =
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException";

    @Test
    void throwsSpecificExceptionForSqlServerUntrustedCertificate() throws ReflectiveOperationException {
        final SQLException sqlException = new SQLException(
                "The certificate chain was issued by an authority that is not trusted.",
                "08S01",
                new CertPathBuilderException("unable to find valid certification path"));

        final Throwable thrown = catchThrowable(() -> throwFlywayException(sqlException));

        assertThat(thrown).isInstanceOf(FlywaySqlException.class);
        assertThat(thrown.getCause()).isSameAs(sqlException);

        if (hasMethod(FlywaySqlException.class, "throwFlywayExceptionIfPossible", SQLException.class, javax.sql.DataSource.class)) {
            assertThat(thrown.getClass().getName()).isEqualTo(SPECIFIC_EXCEPTION_CLASS_NAME);
            assertThat(invokeStringMethod(thrown, "getSqlState")).isEqualTo("08S01");
            assertThat(thrown.getMessage()).contains("The server certificate is not trusted");
        } else {
            assertThat(thrown.getMessage()).isNotBlank();
        }
    }

    private static void throwFlywayException(SQLException sqlException) {
        try {
            final Method throwMethod = FlywaySqlException.class.getMethod(
                    "throwFlywayExceptionIfPossible",
                    SQLException.class,
                    javax.sql.DataSource.class);
            throwMethod.invoke(null, sqlException, null);
        } catch (NoSuchMethodException ignored) {
            throw new FlywaySqlException("Unable to execute statement", sqlException);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            rethrowUnchecked(exception.getCause());
        }
    }

    private static boolean hasMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            type.getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static String invokeStringMethod(Throwable throwable, String methodName)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Method method = throwable.getClass().getMethod(methodName);
        return (String) method.invoke(throwable);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrowUnchecked(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
