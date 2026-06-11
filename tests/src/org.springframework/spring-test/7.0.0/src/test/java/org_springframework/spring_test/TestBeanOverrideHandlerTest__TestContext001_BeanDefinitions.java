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

@Generated
public class TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions {
    @Generated
    public static class TestBeanConfiguration {
        public static BeanDefinition getTestBeanConfigurationBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.TestBeanConfiguration.class);
            beanDefinition.setInstanceSupplier(TestBeanOverrideHandlerTest.TestBeanConfiguration::new);
            return beanDefinition;
        }

        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.MessageService> getMessageServiceInstanceSupplier() {
            return BeanInstanceSupplier.<TestBeanOverrideHandlerTest.MessageService>forFactoryMethod(
                            TestBeanOverrideHandlerTest.TestBeanConfiguration.class, "messageService")
                    .withGenerator((registeredBean) -> registeredBean.getBeanFactory()
                            .getBean("testBeanOverrideHandlerTest.TestBeanConfiguration",
                                    TestBeanOverrideHandlerTest.TestBeanConfiguration.class)
                            .messageService());
        }

        public static BeanDefinition getMessageServiceBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.MessageService.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.TestBeanConfiguration");
            beanDefinition.setInstanceSupplier(getMessageServiceInstanceSupplier());
            return beanDefinition;
        }
    }
}
