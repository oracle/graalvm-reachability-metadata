/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.FunctorException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class FunctorExceptionTest {
    @Test
    void preservesMessageAndCauseWhenCreatedByFunctorCode() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid functor input");

        FunctorException exception = new FunctorException("Unable to invoke functor", cause);
        String stackTrace = stackTrace(exception);

        assertThat(exception)
                .hasMessage("Unable to invoke functor")
                .hasCause(cause);
        assertThat(stackTrace)
                .contains("Unable to invoke functor")
                .contains("invalid functor input");
    }

    private static String stackTrace(FunctorException exception) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            exception.printStackTrace(writer);
        }
        return buffer.toString();
    }
}
