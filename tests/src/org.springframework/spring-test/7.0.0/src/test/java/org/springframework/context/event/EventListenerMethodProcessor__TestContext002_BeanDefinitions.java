/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.context.event;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link EventListenerMethodProcessor}.
 */
@Generated
public class EventListenerMethodProcessor__TestContext002_BeanDefinitions {
    /**
     * Get the bean definition for 'internalEventListenerProcessor'.
     */
    public static BeanDefinition getInternalEventListenerProcessorBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(EventListenerMethodProcessor.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(EventListenerMethodProcessor::new);
        return beanDefinition;
    }
}
