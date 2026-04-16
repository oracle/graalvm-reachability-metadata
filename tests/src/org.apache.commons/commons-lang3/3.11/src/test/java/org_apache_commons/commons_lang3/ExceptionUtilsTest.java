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
    void resolvesLegacyNestedExceptionAccessorsFromTheDefaultCauseMethodList() {
        final IllegalArgumentException nestedCause = new IllegalArgumentException("nested-cause");
        final LegacyWrapperException wrapper = new LegacyWrapperException(nestedCause);

        final Throwable resolvedCause = ExceptionUtils.getCause(wrapper);

        assertThat(resolvedCause).isSameAs(nestedCause);
    }

    public static final class LegacyWrapperException extends Exception {
        private final Throwable nestedException;

        public LegacyWrapperException(final Throwable nestedException) {
            super("wrapper");
            this.nestedException = nestedException;
        }

        public Throwable getNestedException() {
            return nestedException;
        }
    }
}
