/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class ExceptionUtilsTest {

    @Test
    public void getCauseUsesConfiguredLegacyAccessorMethod() {
        IllegalArgumentException cause = new IllegalArgumentException("legacy cause");
        LegacyWrappedException wrapper = new LegacyWrappedException(cause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper, new String[]{"getException"});

        assertThat(resolvedCause).isSameAs(cause);
    }

    @Test
    public void getCauseUsesDefaultLegacyAccessorMethodsWhenDirectCauseIsMissing() {
        UnsupportedOperationException cause = new UnsupportedOperationException("nested");
        LegacyWrappedException wrapper = new LegacyWrappedException(cause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper);

        assertThat(resolvedCause).isSameAs(cause);
    }

    public static final class LegacyWrappedException extends RuntimeException {
        private final Throwable legacyCause;

        public LegacyWrappedException(Throwable legacyCause) {
            super("wrapper without direct cause");
            this.legacyCause = legacyCause;
        }

        public Throwable getException() {
            return legacyCause;
        }
    }
}
