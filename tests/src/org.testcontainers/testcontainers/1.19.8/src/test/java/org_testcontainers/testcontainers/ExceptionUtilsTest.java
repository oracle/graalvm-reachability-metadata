/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.exception.ExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilsTest {
    @Test
    void discoversACauseThroughAConventionalAccessor() {
        IllegalStateException cause = new IllegalStateException("cause");

        assertThat(ExceptionUtils.getCause(new TargetException(cause), new String[] { "getTargetException" }))
            .isSameAs(cause);
    }

    public static class TargetException extends Exception {
        private final Throwable target;

        public TargetException(Throwable target) {
            this.target = target;
        }

        public Throwable getTargetException() {
            return target;
        }
    }
}
