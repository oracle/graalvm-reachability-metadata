/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.MethodMapTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import static org.junit.jupiter.api.Assertions.*;

public class MethodMapTransactionAttributeSourceTest {
    @Test
    void resolvesConfiguredMethodPatternToTransactionAttributes() throws Exception {
        MethodMapTransactionAttributeSource source = new MethodMapTransactionAttributeSource();
        source.setMethodMap(Map.of(TestBeans.GreetingService.class.getName() + ".greet*", "PROPAGATION_REQUIRED,readOnly"));
        source.afterPropertiesSet();
        TransactionAttribute attribute = source.getTransactionAttribute(
                TestBeans.GreetingService.class.getMethod("greet", String.class), TestBeans.GreetingService.class);
        assertNotNull(attribute);
        assertTrue(attribute.isReadOnly());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED, attribute.getPropagationBehavior());
    }
}
