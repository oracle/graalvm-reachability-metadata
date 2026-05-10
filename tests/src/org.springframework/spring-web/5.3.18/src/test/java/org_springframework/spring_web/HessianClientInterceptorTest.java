/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

import com.caucho.hessian.client.HessianProxyFactory;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.remoting.caucho.HessianClientInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

public class HessianClientInterceptorTest {
    @Test
    void invokeDelegatesToPreparedHessianProxy() throws Throwable {
        GreetingService target = new GreetingServiceImpl();
        TestHessianClientInterceptor interceptor = new TestHessianClientInterceptor(target);
        interceptor.setServiceInterface(GreetingService.class);
        interceptor.setServiceUrl("http://localhost:8080/hessian/greeting");
        interceptor.afterPropertiesSet();

        Method method = GreetingService.class.getMethod("greet", String.class);
        Object result = interceptor.invoke(new SimpleMethodInvocation(method, "Spring"));

        assertThat(result).isEqualTo("Hello Spring");
        assertThat(interceptor.isProxyCreated()).isTrue();
    }

    public interface GreetingService {
        String greet(String name);
    }

    private static final class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    private static final class TestHessianClientInterceptor extends HessianClientInterceptor {
        private final Object target;

        private boolean proxyCreated;

        private TestHessianClientInterceptor(Object target) {
            this.target = target;
        }

        @Override
        protected Object createHessianProxy(HessianProxyFactory proxyFactory) {
            this.proxyCreated = true;
            return this.target;
        }

        private boolean isProxyCreated() {
            return this.proxyCreated;
        }
    }

    private static final class SimpleMethodInvocation implements MethodInvocation {
        private final Method method;

        private final Object[] arguments;

        private SimpleMethodInvocation(Method method, Object... arguments) {
            this.method = method;
            this.arguments = arguments;
        }

        @Override
        public Method getMethod() {
            return this.method;
        }

        @Override
        public Object[] getArguments() {
            return this.arguments;
        }

        @Override
        public Object proceed() {
            throw new UnsupportedOperationException("The interceptor invokes the prepared Hessian proxy directly");
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return this.method;
        }
    }
}
