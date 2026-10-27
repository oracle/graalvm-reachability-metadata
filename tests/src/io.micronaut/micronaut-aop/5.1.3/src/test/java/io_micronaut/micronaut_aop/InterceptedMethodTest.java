/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_aop;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class InterceptedMethodTest {
    @Test
    void resolvesTheTargetMethodWhileApplyingAroundAdvice() {
        try (ApplicationContext context = ApplicationContext.run()) {
            TargetMethodService service = context.getBean(TargetMethodService.class);

            assertThat(service.greet("Ada")).isEqualTo("greet:Ada:hello Ada");
        }
    }

    @Singleton
    public static class TargetMethodService {
        @ResolveTargetMethod
        public String greet(String name) {
            return "hello " + name;
        }
    }

    @InterceptorBean(ResolveTargetMethod.class)
    public static class TargetMethodInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.getTargetMethod().getName()
                    + ":"
                    + context.getParameterValues()[0]
                    + ":"
                    + context.proceed();
        }
    }

    @Around
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ResolveTargetMethod {}
}
