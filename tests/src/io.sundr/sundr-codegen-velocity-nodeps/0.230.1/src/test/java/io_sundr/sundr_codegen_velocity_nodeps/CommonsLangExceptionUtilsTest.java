/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class CommonsLangExceptionUtilsTest {

    @Test
    public void setCauseUsesThrowableInitCauseAndLegacySetCauseMethod() {
        IllegalStateException cause = new IllegalStateException("root cause");
        LegacySetCauseException wrapper = new LegacySetCauseException("wrapper");

        boolean updated = ExceptionUtils.setCause(wrapper, cause);

        assertThat(updated).isTrue();
        assertThat(wrapper.getCause()).isSameAs(cause);
        assertThat(wrapper.getLegacyCause()).isSameAs(cause);
    }

    @Test
    public void getCauseUsesReflectiveThrowableAccessorMethod() {
        IllegalArgumentException cause = new IllegalArgumentException("method cause");
        RuntimeException wrapper = new RuntimeException("wrapper", cause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper, new String[] {"getCause"});

        assertThat(resolvedCause).isSameAs(cause);
    }

    @Test
    public void getCauseFallsBackToPublicDetailField() {
        UnsupportedOperationException cause = new UnsupportedOperationException("field cause");
        DetailBackedException wrapper = new DetailBackedException(cause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper, new String[0]);

        assertThat(resolvedCause).isSameAs(cause);
    }

    public static class LegacySetCauseException extends Exception {
        private Throwable legacyCause;

        public LegacySetCauseException(String message) {
            super(message);
        }

        public void setCause(Throwable cause) {
            this.legacyCause = cause;
        }

        public Throwable getLegacyCause() {
            return legacyCause;
        }
    }

    public static class DetailBackedException extends Exception {
        public final Throwable detail;

        public DetailBackedException(Throwable detail) {
            super("detail backed");
            this.detail = detail;
        }
    }
}
