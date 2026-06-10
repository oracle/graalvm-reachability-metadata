package org_springframework.spring_test;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link TestBeanOverrideHandlerTest}.
 */
@Generated
public class TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions {
    /**
     * Bean definitions for {@link TestBeanOverrideHandlerTest.Config}.
     */
    @Generated
    public static class Config {
        /**
         * Get the bean definition for 'config'.
         */
        public static BeanDefinition getConfigBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.Config.class);
            beanDefinition.setInstanceSupplier(TestBeanOverrideHandlerTest.Config::new);
            return beanDefinition;
        }

        /**
         * Get the bean instance supplier for 'greetingService'.
         */
        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.GreetingService> getGreetingServiceInstanceSupplier() {
            return BeanInstanceSupplier.<TestBeanOverrideHandlerTest.GreetingService>forFactoryMethod(
                            TestBeanOverrideHandlerTest.Config.class, "greetingService")
                    .withGenerator((registeredBean) -> registeredBean.getBeanFactory()
                            .getBean("testBeanOverrideHandlerTest.Config", TestBeanOverrideHandlerTest.Config.class)
                            .greetingService());
        }

        /**
         * Get the bean definition for 'greetingService'.
         */
        public static BeanDefinition getGreetingServiceBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.GreetingService.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.Config");
            beanDefinition.setInstanceSupplier(getGreetingServiceInstanceSupplier());
            return beanDefinition;
        }
    }
}
