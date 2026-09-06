/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.ejb.access.AbstractSlsbInvokerInterceptor;

@SuppressWarnings("deprecation")
public class AbstractSlsbInvokerInterceptorTest {

    @Test
    void createFindsAndInvokesNoArgumentCreateMethodOnHomeObject() throws Exception {
        final TestHome home = new TestHome();
        final TestSlsbInvokerInterceptor interceptor = new TestSlsbInvokerInterceptor(home);

        final Object sessionBean = interceptor.createSessionBean();

        assertSame(home.createdBean, sessionBean);
        assertEquals(1, home.createCalls);
        assertEquals(1, interceptor.lookupCalls);
    }

    public static final class TestHome {

        private final TestSessionBean createdBean = new TestSessionBean();

        private int createCalls;

        public TestSessionBean create() {
            this.createCalls++;
            return this.createdBean;
        }
    }

    public static final class TestSessionBean {
    }

    private static final class TestSlsbInvokerInterceptor extends AbstractSlsbInvokerInterceptor {

        private final Object home;

        private int lookupCalls;

        private TestSlsbInvokerInterceptor(Object home) {
            this.home = home;
        }

        private Object createSessionBean() throws NamingException, InvocationTargetException {
            return create();
        }

        @Override
        protected Object lookup() {
            this.lookupCalls++;
            return this.home;
        }

        @Override
        protected Object invokeInContext(MethodInvocation invocation) {
            throw new UnsupportedOperationException("This test exercises create() directly");
        }
    }
}
