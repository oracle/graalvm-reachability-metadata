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
    @SuppressWarnings("deprecation")
    public void getCauseInvokesNamedThrowableAccessor() {
        Throwable nestedCause = new IllegalStateException("nested cause");
        LegacyNestedException wrapper = new LegacyNestedException("wrapper", nestedCause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper, new String[]{"missingAccessor", "getNested"});

        assertThat(resolvedCause).isSameAs(nestedCause);
    }

    @Test
    public void getRootCauseUsesLegacyNestedAccessor() {
        Throwable nestedCause = new IllegalArgumentException("root cause");
        LegacyNestedException wrapper = new LegacyNestedException("wrapper", nestedCause);

        Throwable rootCause = ExceptionUtils.getRootCause(wrapper);

        assertThat(rootCause).isSameAs(nestedCause);
    }

    public static class LegacyNestedException extends Exception {
        private final Throwable nested;

        public LegacyNestedException(String message, Throwable nested) {
            super(message);
            this.nested = nested;
        }

        public Throwable getNested() {
            return nested;
        }
    }
}
