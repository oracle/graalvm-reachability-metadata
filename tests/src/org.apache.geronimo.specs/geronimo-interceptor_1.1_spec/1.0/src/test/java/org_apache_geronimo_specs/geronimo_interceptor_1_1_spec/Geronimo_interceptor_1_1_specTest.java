/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_interceptor_1_1_spec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.ExcludeClassInterceptors;
import javax.interceptor.ExcludeDefaultInterceptors;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.Interceptors;
import javax.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Geronimo_interceptor_1_1_specTest {
    @Test
    void preservesDeclaredInterceptorOrderOnClassesAndMethods() throws Exception {
        Interceptors classInterceptors = Application.class.getAnnotation(Interceptors.class);
        Interceptors methodInterceptors = Application.class.getMethod("execute").getAnnotation(Interceptors.class);

        assertNotNull(classInterceptors);
        assertNotNull(methodInterceptors);
        assertArrayEquals(new Class<?>[] {TracingInterceptor.class, TimingInterceptor.class}, classInterceptors.value());
        assertArrayEquals(new Class<?>[] {TimingInterceptor.class, TracingInterceptor.class}, methodInterceptors.value());
        assertNull(Application.class.getMethod("unmodified").getAnnotation(Interceptors.class));
    }

    @Test
    void exposesCustomBindingOnApplicationAndInterceptor() {
        assertNotNull(Traced.class.getAnnotation(InterceptorBinding.class));
        assertNotNull(Application.class.getAnnotation(Traced.class));
        assertNotNull(TracingInterceptor.class.getAnnotation(Traced.class));
        assertNotNull(TracingInterceptor.class.getAnnotation(Interceptor.class));
        assertEquals("orders", Application.class.getAnnotation(Traced.class).value());
        assertEquals("orders", TracingInterceptor.class.getAnnotation(Traced.class).value());
    }

    @Test
    void distinguishesInvocationAndTimeoutCallbacks() throws Exception {
        Method invocation = TracingInterceptor.class.getMethod("invoke", InvocationContext.class);
        Method timeout = TimingInterceptor.class.getMethod("timeout", InvocationContext.class);

        assertNotNull(invocation.getAnnotation(AroundInvoke.class));
        assertNull(invocation.getAnnotation(AroundTimeout.class));
        assertNotNull(timeout.getAnnotation(AroundTimeout.class));
        assertNull(timeout.getAnnotation(AroundInvoke.class));
    }

    @Test
    void keepsExclusionsScopedToTheirDeclaredClassOrMethod() throws Exception {
        assertNotNull(Application.class.getAnnotation(ExcludeDefaultInterceptors.class));
        Method excluded = Application.class.getMethod("execute");
        assertNotNull(excluded.getAnnotation(ExcludeClassInterceptors.class));
        assertNotNull(excluded.getAnnotation(ExcludeDefaultInterceptors.class));
        assertNull(Application.class.getMethod("unmodified").getAnnotation(ExcludeClassInterceptors.class));
        assertNull(Application.class.getMethod("unmodified").getAnnotation(ExcludeDefaultInterceptors.class));
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Traced {
        String value();
    }

    @Traced("orders")
    @Interceptors({TracingInterceptor.class, TimingInterceptor.class})
    @ExcludeDefaultInterceptors
    public static class Application {
        @Interceptors({TimingInterceptor.class, TracingInterceptor.class})
        @ExcludeClassInterceptors
        @ExcludeDefaultInterceptors
        public void execute() {
        }

        public void unmodified() {
        }
    }

    @Interceptor
    @Traced("orders")
    public static class TracingInterceptor {
        @AroundInvoke
        public Object invoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @Interceptor
    public static class TimingInterceptor {
        @AroundTimeout
        public Object timeout(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }
}
