/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import java.lang.reflect.Method;

import org.jmock.core.Invocation;
import org.jmock.core.stub.DefaultResultStub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultResultStubTest {
    @Test
    void invokeReturnsEmptyArrayForUnregisteredArrayReturnType() throws Throwable {
        DefaultResultStub stub = new DefaultResultStub();
        Invocation invocation = invocationFor("strings");

        Object result = stub.invoke(invocation);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).isEmpty();
    }

    @Test
    void constructorRegistersDefaultReferenceResults() throws Throwable {
        DefaultResultStub stub = new DefaultResultStub();

        assertThat(stub.invoke(invocationFor("text"))).isEqualTo("");
        assertThat(stub.invoke(invocationFor("integer"))).isEqualTo(Integer.valueOf(0));
    }

    private static Invocation invocationFor(String methodName) throws NoSuchMethodException {
        Method method = DefaultResultMethods.class.getMethod(methodName);
        return new Invocation(new DefaultResultMethodsTarget(), method, null);
    }

    public interface DefaultResultMethods {
        String[] strings();

        String text();

        Integer integer();
    }

    private static final class DefaultResultMethodsTarget implements DefaultResultMethods {
        public String[] strings() {
            throw new UnsupportedOperationException("method is not invoked directly");
        }

        public String text() {
            throw new UnsupportedOperationException("method is not invoked directly");
        }

        public Integer integer() {
            throw new UnsupportedOperationException("method is not invoked directly");
        }
    }
}
