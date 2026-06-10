package org.springframework.test.context.support;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link DynamicPropertyRegistrarBeanInitializer}.
 */
@Generated
public class DynamicPropertyRegistrarBeanInitializer__TestContext002_BeanDefinitions {
    /**
     * Get the bean definition for 'internalDynamicPropertyRegistrarBeanInitializer'.
     */
    public static BeanDefinition getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(DynamicPropertyRegistrarBeanInitializer.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(DynamicPropertyRegistrarBeanInitializer::new);
        return beanDefinition;
    }
}
