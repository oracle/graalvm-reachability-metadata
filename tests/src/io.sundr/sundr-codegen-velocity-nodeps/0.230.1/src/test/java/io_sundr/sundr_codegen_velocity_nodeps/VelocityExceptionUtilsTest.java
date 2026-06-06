/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class VelocityExceptionUtilsTest {

    @Test
    public void createAndInitializeExceptionsWithCauses() {
        IllegalArgumentException runtimeCause = new IllegalArgumentException("runtime cause");
        RuntimeException runtimeException = ExceptionUtils.createRuntimeException("runtime wrapper", runtimeCause);

        assertThat(runtimeException)
                .hasMessage("runtime wrapper")
                .hasCause(runtimeCause);

        IllegalStateException initializedCause = new IllegalStateException("initialized cause");
        Exception initializedException = new Exception("initialized wrapper");
        ExceptionUtils.setCause(initializedException, initializedCause);

        assertThat(initializedException).hasCause(initializedCause);

        IllegalArgumentException fallbackCause = new IllegalArgumentException("fallback cause");
        Throwable fallbackException = ExceptionUtils.createWithCause(
                StringOnlyException.class,
                "fallback wrapper",
                fallbackCause);

        assertThat(fallbackException)
                .isInstanceOf(StringOnlyException.class)
                .hasMessageContaining("fallback wrapper")
                .hasMessageContaining("fallback cause");
    }

    public static class StringOnlyException extends Exception {
        public StringOnlyException(String message) {
            super(message);
        }
    }
}
