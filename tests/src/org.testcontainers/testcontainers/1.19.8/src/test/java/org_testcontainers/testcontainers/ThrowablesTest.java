/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.Throwables;

import static org.assertj.core.api.Assertions.assertThat;

public class ThrowablesTest {
    @Test
    void exposesStackFramesThroughTheLazyStackTraceApi() {
        IllegalStateException failure = new IllegalStateException("failure");

        assertThat(Throwables.lazyStackTrace(failure)).isNotEmpty();
        assertThat(Throwables.lazyStackTrace(failure).get(0).getClassName()).isEqualTo(getClass().getName());
    }
}
