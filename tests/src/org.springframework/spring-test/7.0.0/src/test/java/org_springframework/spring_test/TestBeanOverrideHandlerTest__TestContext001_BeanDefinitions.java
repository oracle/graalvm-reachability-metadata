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
public final class TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions {
    private TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions() {
    }

    @Generated
    public static final class TestConfiguration {
        private TestConfiguration() {
        }

        public static BeanDefinition getTestConfigurationBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.TestConfiguration.class);
            beanDefinition.setInstanceSupplier(TestBeanOverrideHandlerTest.TestConfiguration::new);
            return beanDefinition;
        }

        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.GreetingService> getGreetingServiceInstanceSupplier() {
            return BeanInstanceSupplier.<TestBeanOverrideHandlerTest.GreetingService>forFactoryMethod(
                            TestBeanOverrideHandlerTest.TestConfiguration.class,
                            "greetingService"
                    )
                    .withGenerator((registeredBean) -> registeredBean.getBeanFactory()
                            .getBean("testBeanOverrideHandlerTest.TestConfiguration", TestBeanOverrideHandlerTest.TestConfiguration.class)
                            .greetingService());
        }

        public static BeanDefinition getGreetingServiceBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.GreetingService.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.TestConfiguration");
            beanDefinition.setInstanceSupplier(getGreetingServiceInstanceSupplier());
            return beanDefinition;
        }

        private static BeanInstanceSupplier<TestBeanOverrideHandlerTest.GreetingClient> getGreetingClientInstanceSupplier() {
            return BeanInstanceSupplier.<TestBeanOverrideHandlerTest.GreetingClient>forFactoryMethod(
                            TestBeanOverrideHandlerTest.TestConfiguration.class,
                            "greetingClient",
                            TestBeanOverrideHandlerTest.GreetingService.class
                    )
                    .withGenerator((registeredBean, args) -> registeredBean.getBeanFactory()
                            .getBean("testBeanOverrideHandlerTest.TestConfiguration", TestBeanOverrideHandlerTest.TestConfiguration.class)
                            .greetingClient(args.get(0)));
        }

        public static BeanDefinition getGreetingClientBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestBeanOverrideHandlerTest.GreetingClient.class);
            beanDefinition.setDestroyMethodNames("(inferred)");
            beanDefinition.setFactoryBeanName("testBeanOverrideHandlerTest.TestConfiguration");
            beanDefinition.setInstanceSupplier(getGreetingClientInstanceSupplier());
            return beanDefinition;
        }
    }
}
