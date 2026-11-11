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

public class AbstractFactoryBeanInnerEarlySingletonInvocationHandlerTest {
    @Test
    void delegatesEarlyReferenceCallsToInitializedSingleton() throws Exception {
        AbstractFactoryBeanTest.GreetingFactory factory = new AbstractFactoryBeanTest.GreetingFactory();
        AbstractFactoryBeanTest.Greeting early = factory.getObject();
        assertTrue(early.equals(early));
        factory.afterPropertiesSet();
        assertEquals(factory.getObject().message(), early.message());
        assertEquals("hello", early.message());
        factory.destroy();
    }
}
