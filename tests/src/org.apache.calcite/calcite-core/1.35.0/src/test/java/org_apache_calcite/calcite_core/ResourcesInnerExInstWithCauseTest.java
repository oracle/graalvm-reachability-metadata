/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesInnerExInstWithCauseTest {
    @Test
    void createsExceptionUsingMessageAndCauseConstructor() {
        ExceptionMessages messages = Resources.create(ExceptionMessages.class);
        Throwable cause = new IllegalArgumentException("root cause");

        CauseAwareException exception = messages.causeAwareFailure("planner").ex(cause);

        assertThat(exception).hasMessage("Cause-aware failure for planner");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void createsExceptionUsingMessageConstructorAndInitializesCause() {
        ExceptionMessages messages = Resources.create(ExceptionMessages.class);
        Throwable cause = new IllegalStateException("root cause");

        StringOnlyException exception = messages.stringOnlyFailure("optimizer").ex(cause);

        assertThat(exception).hasMessage("String-only failure for optimizer");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    private interface ExceptionMessages {
        @Resources.BaseMessage("Cause-aware failure for {0}")
        Resources.ExInstWithCause<CauseAwareException> causeAwareFailure(String component);

        @Resources.BaseMessage("String-only failure for {0}")
        Resources.ExInstWithCause<StringOnlyException> stringOnlyFailure(String component);
    }

    public static class CauseAwareException extends Exception {
        public CauseAwareException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class StringOnlyException extends Exception {
        public StringOnlyException(String message) {
            super(message);
        }
    }
}
