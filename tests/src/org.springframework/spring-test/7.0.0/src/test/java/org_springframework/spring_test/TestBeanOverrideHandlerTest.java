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
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.BeanOverrideTestExecutionListener;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void invokesTestBeanFactoryMethodWhenPreparingApplicationContext() throws Exception {
        TestBeanTestCase testInstance = new TestBeanTestCase();
        TestContextManager manager = new TestContextManager(TestBeanTestCase.class);

        manager.prepareTestInstance(testInstance);

        ApplicationContext context = manager.getTestContext().getApplicationContext();
        assertThat(testInstance.greetingService).isNotNull();
        assertThat(testInstance.greetingService.greeting()).isEqualTo("test");
        assertThat(context.getBean(GreetingService.class)).isSameAs(testInstance.greetingService);
    }

    @ContextConfiguration(classes = TestBeanConfiguration.class)
    @TestExecutionListeners(BeanOverrideTestExecutionListener.class)
    public static class TestBeanTestCase {
        @TestBean
        private GreetingService greetingService;

        private static GreetingService greetingService() {
            return () -> "test";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestBeanConfiguration {
        @Bean
        GreetingService greetingService() {
            return () -> "production";
        }
    }

    public interface GreetingService {
        String greeting();
    }
}
