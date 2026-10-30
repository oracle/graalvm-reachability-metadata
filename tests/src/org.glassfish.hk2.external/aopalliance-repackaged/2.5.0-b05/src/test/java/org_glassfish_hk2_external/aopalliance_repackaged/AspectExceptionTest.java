/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2_external.aopalliance_repackaged;

import org.aopalliance.aop.AspectException;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class AspectExceptionTest {
    @Test
    void exposesAnInfrastructureFailureMessage() {
        final AspectException failure = new AspectException("Unable to apply tracing advice");

        assertThat(failure).hasMessage("Unable to apply tracing advice").hasNoCause();
        assertThat(failure.toString()).isEqualTo("Unable to apply tracing advice");
    }

    @Test
    void preservesTheUnderlyingFailureAndItsDiagnostics() {
        final IllegalStateException cause = new IllegalStateException("Advice configuration is unavailable");
        final AspectException failure = new AspectException("Unable to initialize advice", cause);
        final StringWriter diagnostics = new StringWriter();
        failure.printStackTrace(new PrintWriter(diagnostics));

        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(diagnostics.toString()).contains("java.lang.IllegalStateException: Advice configuration is unavailable");
    }
}
