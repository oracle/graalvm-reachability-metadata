/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
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
     * Bean definitions for {@link TestBeanOverrideHandlerTest.GreetingServiceConfiguration}.
     */
    @Generated
    public static class GreetingServiceConfiguration {
        /**
         * Get the bean definition for 'greetingServiceConfiguration'.
         */
        public static BeanDefinition getGreetingServiceConfigurationBeanDefinition() {
            RootBeanDefinition beanDefinition =
                    new RootBeanDefinition(TestBeanOverrideHandlerTest.GreetingServiceConfiguration.class);
            beanDefinition.setInstanceSupplier(TestBeanOverrideHandlerTest.GreetingServiceConfiguration::new);
            return beanDefinition;
        }

        /**
         * Get the bean instance supplier for 'greetingService'.
         */
        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.GreetingService> getGreetingServiceInstanceSupplier() {
            return BeanInstanceSupplier
                    .<TestBeanOverrideHandlerTest.GreetingService>forFactoryMethod(
                            TestBeanOverrideHandlerTest.GreetingServiceConfiguration.class, "greetingService")
                    .withGenerator((registeredBean) -> registeredBean.getBeanFactory()
                            .getBean("testBeanOverrideHandlerTest.GreetingServiceConfiguration",
                                    TestBeanOverrideHandlerTest.GreetingServiceConfiguration.class)
                            .greetingService());
        }

        /**
         * Get the bean definition for 'greetingService'.
         */
        public static BeanDefinition getGreetingServiceBeanDefinition() {
            RootBeanDefinition beanDefinition =
                    new RootBeanDefinition(TestBeanOverrideHandlerTest.GreetingService.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.GreetingServiceConfiguration");
            beanDefinition.setInstanceSupplier(getGreetingServiceInstanceSupplier());
            return beanDefinition;
        }
    }
}
