/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import java.lang.reflect.Method;

import org.jmock.core.AbstractDynamicMock;
import org.jmock.core.DynamicMockError;
import org.jmock.core.Invocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractDynamicMockTest {
    @Test
    void unexpectedInvocationFailureIsRememberedButObjectMethodsStillDispatch() throws Throwable {
        ExposedDynamicMock mock = new ExposedDynamicMock(SampleService.class, "mockSampleService");
        Method readMethod = SampleService.class.getMethod("read");
        Method writeMethod = SampleService.class.getMethod("write");
        Method toStringMethod = Object.class.getMethod("toString");

        DynamicMockError failure = captureDynamicMockError(() -> mock.invoke(readMethod));

        assertThat(mock.invoke(toStringMethod)).isEqualTo("mockSampleService");
        assertThatThrownBy(() -> mock.invoke(writeMethod)).isSameAs(failure);
    }

    private static DynamicMockError captureDynamicMockError(ThrowingInvocation invocation) throws Throwable {
        try {
            invocation.invoke();
        } catch (DynamicMockError error) {
            return error;
        }
        throw new AssertionError("Expected a DynamicMockError");
    }

    private interface ThrowingInvocation {
        void invoke() throws Throwable;
    }

    public interface SampleService {
        String read();

        String write();
    }

    private static final class ExposedDynamicMock extends AbstractDynamicMock {
        private final Object proxy = new Object();

        ExposedDynamicMock(Class mockedType, String name) {
            super(mockedType, name);
        }

        public Object proxy() {
            return proxy;
        }

        Object invoke(Method method) throws Throwable {
            return mockInvocation(new Invocation(proxy(), method, null));
        }
    }
}
