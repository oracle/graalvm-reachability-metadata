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
    void invokesTestBeanFactoryMethodWhenPreparingApplicationContext() throws Exception {
        FactoryMethodTestCase testInstance = new FactoryMethodTestCase();
        TestContextManager testContextManager = new TestContextManager(FactoryMethodTestCase.class);

        testContextManager.prepareTestInstance(testInstance);

        ApplicationContext applicationContext = testContextManager.getTestContext().getApplicationContext();
        assertThat(testInstance.greetingService.greeting()).isEqualTo("test bean override");
        assertThat(applicationContext.getBean(GreetingService.class)).isSameAs(testInstance.greetingService);
    }

    @ContextConfiguration(classes = GreetingConfiguration.class)
    static class FactoryMethodTestCase {
        @TestBean
        GreetingService greetingService;

        static GreetingService greetingService() {
            return new TestGreetingService("test bean override");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GreetingConfiguration {
        @Bean
        GreetingService greetingService() {
            return new TestGreetingService("application bean");
        }
    }

    interface GreetingService {
        String greeting();
    }

    static class TestGreetingService implements GreetingService {
        private final String greeting;

        TestGreetingService(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public String greeting() {
            return this.greeting;
        }
    }
}
