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

@Generated
public final class EventListenerMethodProcessor__TestContext001_BeanDefinitions {
    private EventListenerMethodProcessor__TestContext001_BeanDefinitions() {
    }

    public static BeanDefinition getInternalEventListenerProcessorBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(EventListenerMethodProcessor.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(EventListenerMethodProcessor::new);
        return beanDefinition;
    }
}
