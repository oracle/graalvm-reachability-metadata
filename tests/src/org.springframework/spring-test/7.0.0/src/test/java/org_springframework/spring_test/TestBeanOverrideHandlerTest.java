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
        TestBeanTestCase testInstance = new TestBeanTestCase();
        TestContextManager manager = new TestContextManager(TestBeanTestCase.class);

        manager.prepareTestInstance(testInstance);

        ApplicationContext applicationContext = manager.getTestContext().getApplicationContext();
        assertThat(testInstance.service.message()).isEqualTo("from test bean factory");
        assertThat(applicationContext.getBean(MessageService.class)).isSameAs(testInstance.service);
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class TestBeanTestCase {
        @TestBean
        private MessageService service;

        private static MessageService service() {
            return new MessageService("from test bean factory");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        MessageService service() {
            return new MessageService("from application configuration");
        }
    }

    record MessageService(String message) {
    }
}
