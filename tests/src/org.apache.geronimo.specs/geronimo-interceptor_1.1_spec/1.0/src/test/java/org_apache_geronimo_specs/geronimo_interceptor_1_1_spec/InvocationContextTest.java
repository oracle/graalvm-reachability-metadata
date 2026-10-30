/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_interceptor_1_1_spec;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class InvocationContextTest {
    @Test
    void interceptorReplacesArgumentsAndObservesBusinessResult() throws Exception {
        GreetingService service = new GreetingService();
        Method method = GreetingService.class.getMethod("greet", String.class);
        InvocationContext context = new ApplicationInvocation(service, method, new Object[] {"  Ada  "}, null);

        Object result = new ApplicationInterceptor().normalize(context);

        assertEquals("Hello, ADA", result);
        assertArrayEquals(new Object[] {"ADA"}, context.getParameters());
        assertSame(service, context.getTarget());
        assertSame(method, context.getMethod());
        assertNull(context.getTimer());
        assertEquals("greet", context.getContextData().get("operation"));
        assertSame(service, context.getContextData().get("target"));
        assertEquals(result, context.getContextData().get("result"));
        assertEquals(1, service.invocations);
    }

    @Test
    void timeoutInterceptorReadsTimerAndSharesContextWithCallback() throws Exception {
        GreetingService service = new GreetingService();
        Object timer = new Object();
        InvocationContext context = new ApplicationInvocation(service, null, new Object[0], timer);
        context.getContextData().put("job", "cleanup");

        Object result = new ApplicationInterceptor().recordTimeout(context);

        assertNull(result);
        assertNull(context.getMethod());
        assertSame(timer, context.getTimer());
        assertSame(timer, context.getContextData().get("timer"));
        assertEquals("cleanup", context.getContextData().get("job"));
        assertEquals("cleanup", service.lastJob);
        assertEquals(Boolean.TRUE, context.getContextData().get("completed"));
        assertEquals(1, service.invocations);
    }

    @Interceptor
    public static class ApplicationInterceptor {
        @AroundInvoke
        public Object normalize(InvocationContext context) throws Exception {
            context.getContextData().put("operation", context.getMethod().getName());
            context.getContextData().put("target", context.getTarget());
            String name = (String) context.getParameters()[0];
            context.setParameters(new Object[] {name.strip().toUpperCase(Locale.ROOT)});
            Object result = context.proceed();
            context.getContextData().put("result", result);
            return result;
        }

        @AroundTimeout
        public Object recordTimeout(InvocationContext context) throws Exception {
            context.getContextData().put("timer", context.getTimer());
            Object result = context.proceed();
            context.getContextData().put("completed", true);
            return result;
        }
    }

    public static class GreetingService {
        private int invocations;
        private String lastJob;

        public String greet(String name) {
            invocations++;
            return "Hello, " + name;
        }

        public void expire(String job) {
            invocations++;
            lastJob = job;
        }
    }

    // The artifact is an API, so application callbacks receive a test-owned context.
    private static class ApplicationInvocation implements InvocationContext {
        private final GreetingService target;
        private final Method method;
        private final Object timer;
        private final Map<String, Object> contextData = new HashMap<>();
        private Object[] parameters;

        ApplicationInvocation(GreetingService target, Method method, Object[] parameters, Object timer) {
            this.target = target;
            this.method = method;
            this.parameters = parameters;
            this.timer = timer;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getParameters() {
            return parameters;
        }

        @Override
        public void setParameters(Object[] updatedParameters) {
            parameters = updatedParameters;
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object proceed() {
            if (timer != null) {
                target.expire((String) contextData.get("job"));
                return null;
            }
            return target.greet((String) parameters[0]);
        }

        @Override
        public Object getTimer() {
            return timer;
        }
    }
}
