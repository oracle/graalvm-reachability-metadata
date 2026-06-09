/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class QuarkusTestMethodContextTest {
    @Test
    void exposesTestMethodAndInheritedContextState(TestInfo testInfo) {
        Method testMethod = testInfo.getTestMethod().orElseThrow();
        Object testInstance = new Object();
        Object enclosingInstance = new Object();
        IllegalStateException failure = new IllegalStateException("method failed");

        QuarkusTestMethodContext context = new QuarkusTestMethodContext(
                testInstance, List.of(enclosingInstance), testMethod, failure);

        assertThat(context.getTestMethod()).isSameAs(testMethod);
        assertThat(context.getTestInstance()).isSameAs(testInstance);
        assertThat(context.getOuterInstances()).containsExactly(enclosingInstance);
        assertThat(context.getTestStatus().isTestFailed()).isTrue();
        assertThat(context.getTestStatus().getTestErrorCause()).isSameAs(failure);
    }
}
