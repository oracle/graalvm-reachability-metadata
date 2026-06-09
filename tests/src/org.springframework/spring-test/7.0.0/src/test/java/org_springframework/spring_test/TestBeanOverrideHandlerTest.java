/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void invokesFactoryMethodForTestBeanOverride() throws Exception {
        TestBeanScenario.resetFactoryInvocationCount();
        TestBeanScenario testInstance = new TestBeanScenario();
        TestContextManager manager = new TestContextManager(TestBeanScenario.class);

        try {
            manager.prepareTestInstance(testInstance);

            ApplicationContext applicationContext = manager.getTestContext().getApplicationContext();
            GreetingService contextService = applicationContext.getBean(GreetingService.class);

            assertThat(TestBeanScenario.getFactoryInvocationCount()).isEqualTo(1);
            assertThat(testInstance.greetingService).isSameAs(contextService);
            assertThat(testInstance.greetingService.greeting()).isEqualTo("test greeting");
            assertThat(applicationContext.getBean(GreetingConsumer.class).greeting()).isEqualTo("test greeting");
        }
        finally {
            manager.getTestContext().markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
        }
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class TestBeanScenario {
        private static int factoryInvocationCount;

        @TestBean
        GreetingService greetingService;

        static void resetFactoryInvocationCount() {
            factoryInvocationCount = 0;
        }

        static int getFactoryInvocationCount() {
            return factoryInvocationCount;
        }

        private static GreetingService greetingService() {
            factoryInvocationCount++;
            return () -> "test greeting";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        GreetingService greetingService() {
            return () -> "production greeting";
        }

        @Bean
        GreetingConsumer greetingConsumer(GreetingService greetingService) {
            return new GreetingConsumer(greetingService);
        }
    }

    interface GreetingService {
        String greeting();
    }

    static class GreetingConsumer {
        private final GreetingService greetingService;

        GreetingConsumer(GreetingService greetingService) {
            this.greetingService = greetingService;
        }

        String greeting() {
            return this.greetingService.greeting();
        }
    }
}
