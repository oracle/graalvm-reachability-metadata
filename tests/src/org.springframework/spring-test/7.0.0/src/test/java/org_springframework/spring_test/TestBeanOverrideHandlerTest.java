/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void invokesPrivateFactoryMethodForTestBeanOverride() throws Exception {
        TestBeanTestCase.factoryInvocations = 0;
        TestBeanTestCase testInstance = new TestBeanTestCase();
        TestContextManager testContextManager = new TestContextManager(TestBeanTestCase.class);

        testContextManager.prepareTestInstance(testInstance);
        ApplicationContext applicationContext = testContextManager.getTestContext().getApplicationContext();

        try {
            GreetingService override = applicationContext.getBean(GreetingService.class);
            assertThat(testInstance.greetingService).isSameAs(override);
            assertThat(override.greeting()).isEqualTo("test override");
            assertThat(TestBeanTestCase.factoryInvocations).isEqualTo(1);
        } finally {
            ((ConfigurableApplicationContext) applicationContext).close();
        }
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class TestBeanTestCase {
        static int factoryInvocations;

        @TestBean(name = "greetingService", methodName = "testGreetingService")
        private GreetingService greetingService;

        private static GreetingService testGreetingService() {
            factoryInvocations++;
            return new GreetingService("test override");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        GreetingService greetingService() {
            return new GreetingService("production");
        }
    }

    static final class GreetingService {
        private final String greeting;

        GreetingService(String greeting) {
            this.greeting = greeting;
        }

        String greeting() {
            return this.greeting;
        }
    }
}
