/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_test_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestMethodInvoker;

public class InjectMockAndTestMethodInvokerTest {
    @Test
    void injectMockIsAvailableOnFieldsAndParametersAtRuntime() throws NoSuchFieldException, NoSuchMethodException {
        Retention retention = InjectMock.class.getAnnotation(Retention.class);
        Target target = InjectMock.class.getAnnotation(Target.class);
        Method method = MockInjectionTarget.class.getDeclaredMethod("setService", Service.class);
        Parameter parameter = method.getParameters()[0];

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target.value()).containsExactly(ElementType.FIELD, ElementType.PARAMETER);
        assertThat(MockInjectionTarget.class.getDeclaredField("service").getAnnotation(InjectMock.class)).isNotNull();
        assertThat(parameter.getAnnotation(InjectMock.class)).isNotNull();
    }

    @Test
    void methodInvokerDefaultParameterHandlingIsConservative() {
        TestMethodInvoker invoker = new EchoingMethodInvoker();

        assertThat(invoker.handlesMethodParamType(String.class.getName())).isFalse();
        assertThatThrownBy(() -> invoker.methodParamInstance(String.class.getName()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Should never be called");
    }

    @Test
    void methodInvokerCanSelectAndInvokeSupportedTestMethods() throws Throwable {
        TestMethodInvoker invoker = new EchoingMethodInvoker();
        Method echo = InvokedTestCase.class.getDeclaredMethod("echo", String.class);
        Method ignored = InvokedTestCase.class.getDeclaredMethod("ignored");
        InvokedTestCase testInstance = new InvokedTestCase();

        assertThat(invoker.supportsMethod(InvokedTestCase.class, echo)).isTrue();
        assertThat(invoker.supportsMethod(InvokedTestCase.class, ignored)).isFalse();
        assertThat(invoker.invoke(testInstance, echo, List.of("payload"), InvokedTestCase.class.getName()))
                .isEqualTo("invoked:payload");
        assertThat(testInstance.invocationCount).isOne();
    }

    static class MockInjectionTarget {
        @InjectMock
        Service service;

        void setService(@InjectMock Service service) {
            this.service = service;
        }
    }

    interface Service {
    }

    public static class InvokedTestCase {
        int invocationCount;

        public String echo(String value) {
            invocationCount++;
            return "invoked:" + value;
        }

        public void ignored() {
            invocationCount++;
        }
    }

    public static class EchoingMethodInvoker implements TestMethodInvoker {
        @Override
        public boolean supportsMethod(Class<?> originalTestClass, Method originalTestMethod) {
            return originalTestClass.equals(InvokedTestCase.class) && originalTestMethod.getName().equals("echo");
        }

        @Override
        public Object invoke(Object actualTestInstance, Method actualTestMethod, List<Object> actualTestMethodArgs,
                String testClassName) throws Throwable {
            assertThat(testClassName).isEqualTo(InvokedTestCase.class.getName());
            return actualTestMethod.invoke(actualTestInstance, actualTestMethodArgs.toArray());
        }
    }
}
