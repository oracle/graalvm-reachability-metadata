/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutowireUtilsInnerObjectFactoryDelegatingInvocationHandlerTest {
    @Test
    void resolvesFreshObjectForEveryDelegatedInvocation() {
        AutowireUtilsTest.SequenceFactory sequence = new AutowireUtilsTest.SequenceFactory();
        AutowireUtilsTest.Client client = AutowireUtilsTest.createClient(sequence);
        assertEquals("value-1", client.service.read());
        assertEquals("value-2", client.service.read());
        assertEquals(2, sequence.calls);
        assertTrue(client.service.equals(client.service));
    }
}
