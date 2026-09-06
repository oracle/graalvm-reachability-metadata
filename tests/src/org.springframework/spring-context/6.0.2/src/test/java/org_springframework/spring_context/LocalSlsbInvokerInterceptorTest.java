/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.ejb.access.LocalSlsbInvokerInterceptor;

@SuppressWarnings("deprecation")
public class LocalSlsbInvokerInterceptorTest {

    @Test
    void invokesMethodDeclaredOnImplementedBusinessInterface() throws Exception {
        final DirectSessionBean sessionBean = new DirectSessionBean();
        final TestLocalSlsbInvokerInterceptor interceptor = new TestLocalSlsbInvokerInterceptor(sessionBean);
        final MethodInvocation invocation = new SimpleMethodInvocation(
                DirectBusinessService.class.getMethod("greet", String.class), "Spring");

        final Object result = assertDoesNotThrow(() -> interceptor.invoke(invocation));

        assertEquals("direct:Spring", result);
        assertEquals("Spring", sessionBean.lastName);
    }

    @Test
    void resolvesAndInvokesMatchingMethodOnConcreteSessionBeanClass() throws Exception {
        final IndirectSessionBean sessionBean = new IndirectSessionBean();
        final TestLocalSlsbInvokerInterceptor interceptor = new TestLocalSlsbInvokerInterceptor(sessionBean);
        final MethodInvocation invocation = new SimpleMethodInvocation(
                IndirectBusinessService.class.getMethod("greet", String.class), "EJB");

        final Object result = assertDoesNotThrow(() -> interceptor.invoke(invocation));

        assertEquals("indirect:EJB", result);
        assertEquals("EJB", sessionBean.lastName);
    }

    public interface DirectBusinessService {

        String greet(String name);
    }

    public interface IndirectBusinessService {

        String greet(String name);
    }

    public static final class DirectSessionBean implements DirectBusinessService {

        private String lastName;

        @Override
        public String greet(String name) {
            this.lastName = name;
            return "direct:" + name;
        }
    }

    public static final class IndirectSessionBean {

        private String lastName;

        public String greet(String name) {
            this.lastName = name;
            return "indirect:" + name;
        }
    }

    private static final class TestLocalSlsbInvokerInterceptor extends LocalSlsbInvokerInterceptor {

        private final Object sessionBean;

        private TestLocalSlsbInvokerInterceptor(Object sessionBean) {
            this.sessionBean = sessionBean;
        }

        @Override
        protected Object getSessionBeanInstance() {
            return this.sessionBean;
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
            throw new UnsupportedOperationException("Local SLSB invocations are terminal");
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
