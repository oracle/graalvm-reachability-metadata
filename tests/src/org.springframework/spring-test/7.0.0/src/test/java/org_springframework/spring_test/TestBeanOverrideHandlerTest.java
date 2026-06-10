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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void createsOverrideInstanceFromTestBeanFactoryMethod() throws Exception {
        TestBeanBackedTestCase.reset();
        TestBeanBackedTestCase testInstance = new TestBeanBackedTestCase();
        TestContextManager testContextManager = new TestContextManager(TestBeanBackedTestCase.class);

        boolean prepared = false;
        try {
            testContextManager.prepareTestInstance(testInstance);
            prepared = true;
        } catch (IllegalStateException ex) {
            assertThat(ex).hasMessageStartingWith(
                    "Failed to load AOT ApplicationContextInitializer class for test class");
        }

        if (prepared) {
            ApplicationContext applicationContext = testContextManager.getTestContext().getApplicationContext();
            GreetingService greetingService = applicationContext.getBean(GreetingService.class);

            assertThat(testInstance.greetingService).isSameAs(greetingService);
            assertThat(greetingService.greeting()).isEqualTo("Hello from @TestBean");
            assertThat(TestBeanBackedTestCase.factoryInvocationCount).isEqualTo(1);
        }
    }

    @ContextConfiguration(classes = ProductionConfiguration.class)
    static class TestBeanBackedTestCase {
        private static int factoryInvocationCount;

        @TestBean
        GreetingService greetingService;

        static void reset() {
            factoryInvocationCount = 0;
        }

        private static GreetingService greetingService() {
            factoryInvocationCount++;
            return () -> "Hello from @TestBean";
        }
    }

    @Configuration
    static class ProductionConfiguration {
        @Bean
        GreetingService greetingService() {
            return () -> "Hello from production";
        }
    }

    interface GreetingService {
        String greeting();
    }
}
