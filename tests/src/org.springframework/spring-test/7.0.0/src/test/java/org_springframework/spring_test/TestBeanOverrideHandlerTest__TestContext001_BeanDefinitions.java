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
    public static class TestConfiguration {
        public static BeanDefinition getTestConfigurationBeanDefinition() {
            RootBeanDefinition beanDefinition =
                    new RootBeanDefinition(TestBeanOverrideHandlerTest.TestConfiguration.class);
            beanDefinition.setInstanceSupplier(TestBeanOverrideHandlerTest.TestConfiguration::new);
            return beanDefinition;
        }

        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.MessageService> getMessageServiceInstanceSupplier() {
            return BeanInstanceSupplier
                    .<TestBeanOverrideHandlerTest.MessageService>forFactoryMethod(
                            TestBeanOverrideHandlerTest.TestConfiguration.class,
                            "messageService"
                    )
                    .withGenerator((registeredBean) -> registeredBean.getBeanFactory()
                            .getBean(
                                    "testBeanOverrideHandlerTest.TestConfiguration",
                                    TestBeanOverrideHandlerTest.TestConfiguration.class
                            )
                            .messageService());
        }

        public static BeanDefinition getMessageServiceBeanDefinition() {
            RootBeanDefinition beanDefinition =
                    new RootBeanDefinition(TestBeanOverrideHandlerTest.MessageService.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.TestConfiguration");
            beanDefinition.setInstanceSupplier(getMessageServiceInstanceSupplier());
            return beanDefinition;
        }

        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.MessageClient> getMessageClientInstanceSupplier() {
            return BeanInstanceSupplier
                    .<TestBeanOverrideHandlerTest.MessageClient>forFactoryMethod(
                            TestBeanOverrideHandlerTest.TestConfiguration.class,
                            "messageClient",
                            TestBeanOverrideHandlerTest.MessageService.class
                    )
                    .withGenerator((registeredBean, args) -> registeredBean.getBeanFactory()
                            .getBean(
                                    "testBeanOverrideHandlerTest.TestConfiguration",
                                    TestBeanOverrideHandlerTest.TestConfiguration.class
                            )
                            .messageClient(args.get(0)));
        }

        public static BeanDefinition getMessageClientBeanDefinition() {
            RootBeanDefinition beanDefinition =
                    new RootBeanDefinition(TestBeanOverrideHandlerTest.MessageClient.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.TestConfiguration");
            beanDefinition.setInstanceSupplier(getMessageClientInstanceSupplier());
            return beanDefinition;
        }
    }
}
