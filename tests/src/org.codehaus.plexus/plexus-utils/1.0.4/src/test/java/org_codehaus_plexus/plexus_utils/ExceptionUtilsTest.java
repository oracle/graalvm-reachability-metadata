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
    void extractsCauseUsingCustomPublicMethod() {
        Throwable rootCause = new IllegalStateException("root cause");
        MethodBasedWrapper wrapper = new MethodBasedWrapper(rootCause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper, new String[] {"getNested"});

        assertThat(resolvedCause).isSameAs(rootCause);
    }

    @Test
    void extractsCauseUsingPublicDetailFieldWhenNoMethodMatches() {
        Throwable rootCause = new IllegalArgumentException("field cause");
        FieldBasedWrapper wrapper = new FieldBasedWrapper(rootCause);

        Throwable resolvedCause = ExceptionUtils.getCause(wrapper, new String[] {"missingAccessor"});

        assertThat(resolvedCause).isSameAs(rootCause);
    }

    @Test
    void detectsNestedThrowableUsingMethodDiscovery() {
        Throwable rootCause = new IllegalStateException("nested cause");
        MethodBasedWrapper wrapper = new MethodBasedWrapper(rootCause);

        boolean nestedThrowable = ExceptionUtils.isNestedThrowable(wrapper);

        assertThat(nestedThrowable).isTrue();
    }

    @Test
    void detectsNestedThrowableUsingDetailFieldWhenMethodDiscoveryDoesNotMatch() {
        Throwable rootCause = new IllegalArgumentException("detail cause");
        FieldBasedWrapper wrapper = new FieldBasedWrapper(rootCause);
        String[] previousMethodNames = CauseMethodNamesOverride.replaceCauseMethodNames("missingAccessor");

        try {
            boolean nestedThrowable = ExceptionUtils.isNestedThrowable(wrapper);

            assertThat(nestedThrowable).isTrue();
        } finally {
            CauseMethodNamesOverride.replaceCauseMethodNames(previousMethodNames);
        }
    }

    public static class MethodBasedWrapper extends Exception {
        private final Throwable nested;

        public MethodBasedWrapper(Throwable nested) {
            super("method wrapper");
            this.nested = nested;
        }

        public Throwable getNested() {
            return nested;
        }
    }

    public static class FieldBasedWrapper extends Exception {
        public final Throwable detail;

        public FieldBasedWrapper(Throwable detail) {
            super("field wrapper");
            this.detail = detail;
        }
    }

    public static class CauseMethodNamesOverride extends ExceptionUtils {
        static String[] replaceCauseMethodNames(String... methodNames) {
            String[] previousMethodNames = CAUSE_METHOD_NAMES;
            CAUSE_METHOD_NAMES = methodNames;
            return previousMethodNames;
        }
    }
}
