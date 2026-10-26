/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.com.google.common.base.Throwables;
import org.junit.jupiter.api.Test;

public class ThrowablesTest {
    @Test
    void exposesTheLazyStackTrace() {
        IllegalStateException failure = new IllegalStateException("pulsar");

        List<StackTraceElement> trace = Throwables.lazyStackTrace(failure);

        assertThat(trace).isNotEmpty();
        assertThat(trace.get(0).getClassName()).isEqualTo(getClass().getName());
    }
}
