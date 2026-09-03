/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import org.jmock.Mock;
import org.jmock.core.stub.ReturnStub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InvocationMockerBuilderTest {
    public interface Service {
        String name();
    }

    @Test
    void stubsObjectMethodNameValidatedAgainstMockedTypeAndObjectMethods() {
        Mock serviceMock = new Mock(Service.class, "service");
        serviceMock.stubs()
                .method("toString")
                .withNoArguments()
                .will(new ReturnStub("stubbed service"));

        Service service = (Service) serviceMock.proxy();

        assertThat(service.toString()).isEqualTo("stubbed service");
    }
}
