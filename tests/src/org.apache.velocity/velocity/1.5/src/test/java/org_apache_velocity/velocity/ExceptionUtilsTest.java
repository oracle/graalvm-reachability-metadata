/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilsTest {
    @Test
    void createsAndLinksExceptionsUsingPublicExceptionUtilsApi() {
        Throwable rootCause = new IllegalArgumentException("root cause");

        RuntimeException runtimeException = ExceptionUtils.createRuntimeException("runtime wrapper", rootCause);

        assertThat(runtimeException)
                .hasMessage("runtime wrapper")
                .hasCause(rootCause);

        Throwable initialized = new Exception("initialized later");
        Throwable initializedCause = new IllegalStateException("initialized cause");

        ExceptionUtils.setCause(initialized, initializedCause);

        assertThat(initialized).hasCause(initializedCause);

        Throwable legacyCause = new IllegalArgumentException("legacy cause");
        Throwable legacyException = ExceptionUtils.createWithCause(
                MessageOnlyException.class,
                "legacy wrapper",
                legacyCause);

        assertThat(legacyException)
                .isInstanceOf(MessageOnlyException.class)
                .hasMessageContaining("legacy wrapper caused by")
                .hasMessageContaining("legacy cause");
        assertThat(legacyException.getCause()).isNull();
    }

    public static class MessageOnlyException extends Exception {
        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
