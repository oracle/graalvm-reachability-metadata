/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_aop;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.aop.Around;
import io.micronaut.aop.AroundConstruct;
import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class Micronaut_aopTest {
    @Test
    void appliesAnAroundInterceptorToAConcreteBeanMethod() {
        try (ApplicationContext context = ApplicationContext.run()) {
            GreetingService service = context.getBean(GreetingService.class);

            assertThat(service.greet("Ada")).isEqualTo("greet:Ada:hello Ada");
        }
    }

    @Test
    void implementsAnIntroductionThroughAnInterceptor() {
        try (ApplicationContext context = ApplicationContext.run()) {
            IntroducedGreeting greeting = context.getBean(IntroducedGreeting.class);

            assertThat(greeting.greet("Grace")).isEqualTo("introduced:Grace");
        }
    }

    @Test
    void appliesAnAroundInterceptorDeclaredOnAType() {
        try (ApplicationContext context = ApplicationContext.run()) {
            TypeAuditedService service = context.getBean(TypeAuditedService.class);

            assertThat(service.greet("Lin")).isEqualTo("greet:Lin:hello Lin");
            assertThat(service.farewell("Lin")).isEqualTo("farewell:Lin:goodbye Lin");
        }
    }

    @Test
    void appliesAnAroundConstructInterceptorDuringBeanCreation() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ConstructedService service = context.getBean(ConstructedService.class);

            assertThat(service.lifecycle()).isEqualTo("constructor:intercepted");
        }
    }

    @Singleton
    public static class GreetingService {
        @Audited
        public String greet(String name) {
            return "hello " + name;
        }
    }

    @Audited
    @Singleton
    public static class TypeAuditedService {
        public String greet(String name) {
            return "hello " + name;
        }

        public String farewell(String name) {
            return "goodbye " + name;
        }
    }

    @Constructed
    @Singleton
    public static class ConstructedService {
        private String lifecycle = "constructor";

        public String lifecycle() {
            return lifecycle;
        }

        public void markIntercepted() {
            lifecycle += ":intercepted";
        }
    }

    @InterceptorBean(Constructed.class)
    public static class ConstructedInterceptor implements ConstructorInterceptor<Object> {
        @Override
        public Object intercept(ConstructorInvocationContext<Object> context) {
            ConstructedService service = (ConstructedService) context.proceed();
            service.markIntercepted();
            return service;
        }
    }

    @InterceptorBean(Audited.class)
    public static class AuditedInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            Object[] parameterValues = context.getParameterValues();
            return context.getMethodName()
                    + ":"
                    + parameterValues[0]
                    + ":"
                    + context.proceed();
        }
    }

    @AroundConstruct
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Constructed {}

    @Around
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface Audited {}

    @GeneratedGreeting
    @Singleton
    public interface IntroducedGreeting {
        @Executable
        String greet(String name);
    }

    @InterceptorBean(GeneratedGreeting.class)
    public static class GeneratedGreetingInterceptor
            implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return "introduced:" + context.getParameterValues()[0];
        }
    }

    @Introduction
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface GeneratedGreeting {}
}
