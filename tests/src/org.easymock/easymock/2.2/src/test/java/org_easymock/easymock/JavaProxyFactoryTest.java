/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_easymock.easymock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.junit.jupiter.api.Test;

public class JavaProxyFactoryTest {
    @Test
    public void createsInterfaceMockWithRecordedReturnValue() {
        GreetingService service = createMock(GreetingService.class);

        expect(service.greet("metadata")).andReturn("hello metadata");
        replay(service);

        assertThat(service.greet("metadata")).isEqualTo("hello metadata");
        verify(service);
    }

    public interface GreetingService {
        String greet(String name);
    }
}
