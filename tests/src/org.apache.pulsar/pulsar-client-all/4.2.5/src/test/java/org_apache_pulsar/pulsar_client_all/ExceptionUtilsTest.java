/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ExceptionUtilsTest {
    @Test
    void obtainsCauseThroughConfiguredAccessorMethod() {
        IllegalStateException cause = new IllegalStateException("broker unavailable");
        NestedFailure failure = new NestedFailure(cause);

        Throwable resolved = ExceptionUtils.getCause(failure, new String[] {"getNestedFailure"});

        assertThat(resolved).isSameAs(cause);
    }

    public static class NestedFailure extends Exception {
        private final Throwable nestedFailure;

        public NestedFailure(Throwable nestedFailure) {
            this.nestedFailure = nestedFailure;
        }

        public Throwable getNestedFailure() {
            return nestedFailure;
        }
    }
}
