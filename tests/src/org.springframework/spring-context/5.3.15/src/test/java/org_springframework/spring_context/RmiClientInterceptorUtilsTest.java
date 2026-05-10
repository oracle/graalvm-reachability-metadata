/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.remoting.rmi.RmiClientInterceptorUtils;

@SuppressWarnings("deprecation")
public class RmiClientInterceptorUtilsTest {

    @Test
    void invokesMethodDirectlyWhenStubImplementsDeclaringInterface() throws Throwable {
        final DirectServiceStub stub = new DirectServiceStub();
        final Method method = DirectService.class.getMethod("echo", String.class);
        final RecordingMethodInvocation invocation = new RecordingMethodInvocation(method, stub, "direct");

        final Object result = RmiClientInterceptorUtils.invokeRemoteMethod(invocation, stub);

        assertEquals("direct:direct", result);
        assertEquals("direct", stub.lastValue);
    }

    @Test
    void locatesMatchingPublicMethodWhenStubDoesNotImplementDeclaringInterface() throws Throwable {
        final IndirectServiceStub stub = new IndirectServiceStub();
        final Method method = ClientService.class.getMethod("echo", String.class);
        final RecordingMethodInvocation invocation = new RecordingMethodInvocation(method, stub, "indirect");

        final Object result = RmiClientInterceptorUtils.invokeRemoteMethod(invocation, stub);

        assertEquals("stub:indirect", result);
        assertEquals("indirect", stub.lastValue);
    }

    public interface DirectService {

        String echo(String value);
    }

    public interface ClientService {

        String echo(String value);
    }

    public static final class DirectServiceStub implements DirectService {

        private String lastValue;

        @Override
        public String echo(String value) {
            this.lastValue = value;
            return "direct:" + value;
        }
    }

    public static final class IndirectServiceStub {

        private String lastValue;

        public String echo(String value) {
            this.lastValue = value;
            return "stub:" + value;
        }
    }

    private static final class RecordingMethodInvocation implements MethodInvocation {

        private final Method method;
        private final Object target;
        private final Object[] arguments;

        RecordingMethodInvocation(Method method, Object target, Object... arguments) {
            this.method = method;
            this.target = target;
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
            throw new UnsupportedOperationException("The test invokes the RMI stub directly");
        }

        @Override
        public Object getThis() {
            return this.target;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return this.method;
        }
    }
}
