/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jdo.jdo2_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.jdo.JDOException;
import javax.jdo.JDOFatalInternalException;

import org.junit.jupiter.api.Test;

public class JDOExceptionTest {
    @Test
    void includesTheFailedObjectAndAllNestedFailuresInDiagnostics() {
        IllegalArgumentException first = new IllegalArgumentException("invalid account key");
        IllegalStateException second = new IllegalStateException("transaction closed");
        JDOException failure = new JDOException("Cannot save account", new Throwable[]{first, second}, "account:42");

        assertThat(failure.getFailedObject()).isEqualTo("account:42");
        assertThat(failure.getNestedExceptions()).containsExactly(first, second);
        assertThat(failure.getCause()).isSameAs(first);
        assertThat(failure.toString()).contains("Cannot save account", "account:42",
                "invalid account key", "transaction closed");

        StringWriter output = new StringWriter();
        try (PrintWriter writer = new PrintWriter(output)) {
            failure.printStackTrace(writer);
        }
        assertThat(output.toString()).contains("Cannot save account", "account:42",
                "invalid account key", "transaction closed");
        assertThat(failure.getCause()).isSameAs(first);
    }

    @Test
    void requiresNestedExceptionsToBeProvidedThroughItsConstructor() {
        JDOException failure = new JDOException("Cannot save account");

        assertThatThrownBy(() -> failure.initCause(new IllegalStateException("transaction closed")))
                .isInstanceOf(JDOFatalInternalException.class)
                .hasMessageContaining("initCause");
        assertThat(failure.getCause()).isNull();
    }
}
