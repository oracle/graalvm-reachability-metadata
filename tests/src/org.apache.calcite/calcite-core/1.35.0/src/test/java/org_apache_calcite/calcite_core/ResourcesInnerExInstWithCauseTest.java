/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesInnerExInstWithCauseTest {
    @Test
    void createsExceptionsUsingBothSupportedConstructorShapes() {
        FailureResources resources = Resources.create(FailureResources.class);
        IllegalStateException cause = new IllegalStateException("root cause");

        MessageAndCauseException first = resources.withCause("alpha").ex(cause);
        MessageOnlyException second = resources.messageOnly("beta").ex(cause);

        assertThat(first).hasMessage("Failure alpha").hasCause(cause);
        assertThat(second).hasMessage("Failure beta").hasCause(cause);
    }

    public interface FailureResources {
        @Resources.BaseMessage("Failure {0}")
        Resources.ExInstWithCause<MessageAndCauseException> withCause(String value);

        @Resources.BaseMessage("Failure {0}")
        Resources.ExInstWithCause<MessageOnlyException> messageOnly(String value);
    }

    public static class MessageAndCauseException extends Exception {
        public MessageAndCauseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MessageOnlyException extends Exception {
        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
