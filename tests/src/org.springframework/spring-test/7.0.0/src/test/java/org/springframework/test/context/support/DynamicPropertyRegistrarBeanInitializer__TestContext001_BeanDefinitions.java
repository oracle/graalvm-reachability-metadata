/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.test.context.support;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link DynamicPropertyRegistrarBeanInitializer}.
 */
@Generated
public class DynamicPropertyRegistrarBeanInitializer__TestContext001_BeanDefinitions {
    /**
     * Get the bean definition for
     * 'internalDynamicPropertyRegistrarBeanInitializer'.
     */
    public static BeanDefinition getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition() {
        RootBeanDefinition beanDefinition =
                new RootBeanDefinition(DynamicPropertyRegistrarBeanInitializer.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(DynamicPropertyRegistrarBeanInitializer::new);
        return beanDefinition;
    }
}
