/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import org.jmock.core.Invocation;
import org.jmock.core.stub.DefaultResultStub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultResultStubTest {
    public interface ArrayReturningService {
        String[] names();
    }

    @Test
    void returnsEmptyArrayForArrayReturnType() throws Throwable {
        DefaultResultStub stub = new DefaultResultStub();
        Invocation invocation = new Invocation(
                "service",
                ArrayReturningService.class.getMethod("names"),
                new Object[0]);

        Object result = stub.invoke(invocation);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).isEmpty();
    }
}
