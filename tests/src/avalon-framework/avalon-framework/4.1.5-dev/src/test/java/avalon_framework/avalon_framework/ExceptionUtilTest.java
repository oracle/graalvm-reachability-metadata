/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package avalon_framework.avalon_framework;

import org.apache.avalon.framework.ExceptionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilTest {
    @Test
    void getCauseUsesThrowableGetCauseWhenReflectionIsEnabled() {
        IllegalStateException rootCause = new IllegalStateException("root cause");
        RuntimeException wrapper = new RuntimeException("wrapper", rootCause);

        Throwable resolvedCause = ExceptionUtil.getCause(wrapper, true);

        assertThat(resolvedCause).isSameAs(rootCause);
    }

    @Test
    void getCauseIgnoresStandardThrowableCauseWhenReflectionIsDisabled() {
        IllegalStateException rootCause = new IllegalStateException("root cause");
        RuntimeException wrapper = new RuntimeException("wrapper", rootCause);

        Throwable resolvedCause = ExceptionUtil.getCause(wrapper, false);

        assertThat(resolvedCause).isNull();
    }

    @Test
    void printStackTraceIncludesReflectedCauseWhenRequested() {
        IllegalArgumentException rootCause = new IllegalArgumentException("configuration value is missing");
        IllegalStateException wrapper = new IllegalStateException("startup failed", rootCause);

        String stackTrace = ExceptionUtil.printStackTrace(wrapper, 0, true, true);

        assertThat(stackTrace)
                .contains("java.lang.IllegalStateException: startup failed")
                .contains("rethrown from")
                .contains("java.lang.IllegalArgumentException: configuration value is missing");
    }
}
