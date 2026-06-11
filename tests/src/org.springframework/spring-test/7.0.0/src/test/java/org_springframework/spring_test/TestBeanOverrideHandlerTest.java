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
    void invokesTestBeanFactoryMethodForOverrideInstance() throws Exception {
        TestBeanTestCase testInstance = new TestBeanTestCase();
        TestContextManager testContextManager = new TestContextManager(TestBeanTestCase.class);

        testContextManager.prepareTestInstance(testInstance);

        ApplicationContext applicationContext = testContextManager.getTestContext().getApplicationContext();
        MessageService override = applicationContext.getBean(MessageService.class);
        assertThat(testInstance.messageService).isSameAs(override);
        assertThat(override.message()).isEqualTo("test override");
    }

    @ContextConfiguration(classes = TestBeanConfiguration.class)
    public static class TestBeanTestCase {
        @TestBean
        private MessageService messageService;

        private static MessageService messageService() {
            return () -> "test override";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeanConfiguration {
        @Bean
        MessageService messageService() {
            return () -> "application bean";
        }
    }

    interface MessageService {
        String message();
    }
}
