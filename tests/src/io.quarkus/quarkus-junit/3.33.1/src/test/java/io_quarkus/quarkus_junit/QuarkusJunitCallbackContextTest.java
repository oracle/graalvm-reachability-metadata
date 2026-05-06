/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.test.junit.callback.QuarkusTestAfterAllCallback;
import io.quarkus.test.junit.callback.QuarkusTestAfterConstructCallback;
import io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestAfterTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeClassCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestContext;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.junit.jupiter.api.Test;

public class QuarkusJunitCallbackContextTest {
    @Test
    void testContextRetainsInstanceOuterInstancesAndSuccessfulStatus() {
        Object testInstance = new Object();
        List<Object> outerInstances = List.of("outer", 42);

        QuarkusTestContext context = new QuarkusTestContext(testInstance, outerInstances, null);

        assertThat(context.getTestInstance()).isSameAs(testInstance);
        assertThat(context.getOuterInstances()).containsExactly("outer", 42);
        assertThat(context.getTestStatus().isTestFailed()).isFalse();
        assertThat(context.getTestStatus().getTestErrorCause()).isNull();
    }

    @Test
    void methodContextRetainsMethodAndFailureStatus() throws NoSuchMethodException {
        IllegalStateException failure = new IllegalStateException("boom");
        Method method = Object.class.getMethod("toString");

        QuarkusTestMethodContext context = new QuarkusTestMethodContext("test", List.of(), method, failure);

        assertThat(context.getTestInstance()).isEqualTo("test");
        assertThat(context.getOuterInstances()).isEmpty();
        assertThat(context.getTestMethod()).isSameAs(method);
        assertThat(context.getTestStatus().isTestFailed()).isTrue();
        assertThat(context.getTestStatus().getTestErrorCause()).isSameAs(failure);
    }

    @Test
    void callbackInterfacesCanBeComposedAroundAContext() throws NoSuchMethodException {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        Object testInstance = new Object();
        Method method = Object.class.getMethod("toString");
        QuarkusTestMethodContext methodContext = new QuarkusTestMethodContext(testInstance, List.of("outer"), method,
                null);
        QuarkusTestContext classContext = new QuarkusTestContext(testInstance, List.of("outer"), null);

        callbacks.afterConstruct(testInstance);
        callbacks.beforeClass(QuarkusJunitCallbackContextTest.class);
        callbacks.beforeEach(methodContext);
        callbacks.beforeTestExecution(methodContext);
        callbacks.afterTestExecution(methodContext);
        callbacks.afterEach(methodContext);
        callbacks.afterAll(classContext);

        assertThat(callbacks.events).containsExactly(
                "afterConstruct:Object",
                "beforeClass:QuarkusJunitCallbackContextTest",
                "beforeEach:toString",
                "beforeTestExecution:toString",
                "afterTestExecution:toString",
                "afterEach:toString",
                "afterAll:false");
    }

    private static final class RecordingCallbacks implements QuarkusTestAfterConstructCallback,
            QuarkusTestBeforeClassCallback, QuarkusTestBeforeEachCallback, QuarkusTestBeforeTestExecutionCallback,
            QuarkusTestAfterTestExecutionCallback, QuarkusTestAfterEachCallback, QuarkusTestAfterAllCallback {
        private final List<String> events = new ArrayList<>();

        @Override
        public void afterConstruct(Object testInstance) {
            events.add("afterConstruct:" + testInstance.getClass().getSimpleName());
        }

        @Override
        public void beforeClass(Class<?> testClass) {
            events.add("beforeClass:" + testClass.getSimpleName());
        }

        @Override
        public void beforeEach(QuarkusTestMethodContext context) {
            events.add("beforeEach:" + context.getTestMethod().getName());
        }

        @Override
        public void beforeTestExecution(QuarkusTestMethodContext context) {
            events.add("beforeTestExecution:" + context.getTestMethod().getName());
        }

        @Override
        public void afterTestExecution(QuarkusTestMethodContext context) {
            events.add("afterTestExecution:" + context.getTestMethod().getName());
        }

        @Override
        public void afterEach(QuarkusTestMethodContext context) {
            events.add("afterEach:" + context.getTestMethod().getName());
        }

        @Override
        public void afterAll(QuarkusTestContext context) {
            events.add("afterAll:" + context.getTestStatus().isTestFailed());
        }
    }
}
