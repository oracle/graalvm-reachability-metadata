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
     * Bean definitions for {@link TestBeanOverrideHandlerTest.TestConfiguration}.
     */
    @Generated
    public static class TestConfiguration {
        /**
         * Get the bean definition for 'testConfiguration'.
         */
        public static BeanDefinition getTestConfigurationBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.TestConfiguration.class);
            beanDefinition.setInstanceSupplier(TestBeanOverrideHandlerTest.TestConfiguration::new);
            return beanDefinition;
        }

        /**
         * Get the bean instance supplier for 'testService'.
         */
        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.TestService> getTestServiceInstanceSupplier() {
            return BeanInstanceSupplier.<TestBeanOverrideHandlerTest.TestService>forFactoryMethod(
                    TestBeanOverrideHandlerTest.TestConfiguration.class, "testService")
                    .withGenerator((registeredBean) -> registeredBean.getBeanFactory()
                            .getBean("testBeanOverrideHandlerTest.TestConfiguration",
                                    TestBeanOverrideHandlerTest.TestConfiguration.class)
                            .testService());
        }

        /**
         * Get the bean definition for 'testService'.
         */
        public static BeanDefinition getTestServiceBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.TestService.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.TestConfiguration");
            beanDefinition.setInstanceSupplier(getTestServiceInstanceSupplier());
            return beanDefinition;
        }
    }
}
