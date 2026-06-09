/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.quarkus.test.junit.callback.QuarkusTestContext;
import org.junit.jupiter.api.Test;

public class QuarkusTestContextTest {
    @Test
    void exposesTestInstanceOuterInstancesAndFailureStatus() {
        Object testInstance = new Object();
        Object enclosingInstance = new Object();
        RuntimeException failure = new RuntimeException("test failed");
        QuarkusTestContext context = new QuarkusTestContext(testInstance, List.of(enclosingInstance), failure);

        assertThat(context.getTestInstance()).isSameAs(testInstance);
        assertThat(context.getOuterInstances()).containsExactly(enclosingInstance);
        assertThat(context.getTestStatus().isTestFailed()).isTrue();
        assertThat(context.getTestStatus().getTestErrorCause()).isSameAs(failure);
    }

    @Test
    void reportsSuccessfulStatusWhenNoFailureCauseIsSupplied() {
        QuarkusTestContext context = new QuarkusTestContext(this, List.of(), null);

        assertThat(context.getTestStatus().isTestFailed()).isFalse();
        assertThat(context.getTestStatus().getTestErrorCause()).isNull();
    }
}
