/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilsTest {
    @Test
    void resolvesCauseFromConfiguredPublicMethod() {
        Throwable cause = new IllegalStateException("method cause");
        MethodBackedException wrapper = new MethodBackedException(cause);

        Throwable resolved = ExceptionUtils.getCause(wrapper, new String[] {"getLegacyCause"});

        assertThat(resolved).isSameAs(cause);
    }

    @Test
    void resolvesCauseFromPublicDetailFieldWhenConfiguredMethodsDoNotMatch() {
        Throwable cause = new IllegalArgumentException("field cause");
        FieldBackedException wrapper = new FieldBackedException(cause);

        Throwable resolved = ExceptionUtils.getCause(wrapper, new String[] {"missingCause"});

        assertThat(resolved).isSameAs(cause);
    }

    @Test
    void detectsNestedThrowableFromConfiguredCauseMethod() {
        Throwable cause = new IllegalStateException("nested method cause");
        MethodBackedException wrapper = new MethodBackedException(cause);

        assertThat(ExceptionUtils.isNestedThrowable(wrapper)).isTrue();
    }

    @Test
    void detectsNestedThrowableFromPublicDetailFieldWhenNoCauseMethodsAreConfigured() {
        Throwable cause = new IllegalArgumentException("nested field cause");
        FieldBackedException wrapper = new FieldBackedException(cause);

        ExceptionUtilsAccess.withCauseMethodNames(new String[] {"missingCause"}, () ->
                assertThat(ExceptionUtils.isNestedThrowable(wrapper)).isTrue());
    }

    public static class MethodBackedException extends Exception {
        private final Throwable legacyCause;

        MethodBackedException(Throwable legacyCause) {
            this.legacyCause = legacyCause;
        }

        public Throwable getLegacyCause() {
            return legacyCause;
        }
    }

    public static class FieldBackedException extends Exception {
        public final Throwable detail;

        FieldBackedException(Throwable detail) {
            this.detail = detail;
        }
    }

    private static final class ExceptionUtilsAccess extends ExceptionUtils {
        static void withCauseMethodNames(String[] methodNames, Runnable action) {
            String[] previousMethodNames = CAUSE_METHOD_NAMES;
            CAUSE_METHOD_NAMES = methodNames;
            try {
                action.run();
            } finally {
                CAUSE_METHOD_NAMES = previousMethodNames;
            }
        }
    }
}
