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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void testBeanFactoryMethodCreatesOverrideInstance() throws Exception {
        TestContextManager manager = new TestContextManager(TestBeanTestCase.class);
        TestBeanTestCase testInstance = new TestBeanTestCase();

        manager.beforeTestClass();
        try {
            manager.prepareTestInstance(testInstance);

            ApplicationContext applicationContext = manager.getTestContext().getApplicationContext();
            GreetingService bean = applicationContext.getBean(GreetingService.class);

            assertThat(bean).isSameAs(testInstance.greetingService);
            assertThat(bean.greeting()).isEqualTo("test greeting");
        } finally {
            manager.afterTestClass();
        }
    }

    @ContextConfiguration(classes = GreetingServiceConfiguration.class)
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    static class TestBeanTestCase {
        @TestBean
        GreetingService greetingService;

        static GreetingService greetingService() {
            return new TestGreetingService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GreetingServiceConfiguration {
        @Bean
        GreetingService greetingService() {
            return () -> "application greeting";
        }
    }

    interface GreetingService {
        String greeting();
    }

    static class TestGreetingService implements GreetingService {
        @Override
        public String greeting() {
            return "test greeting";
        }
    }
}
