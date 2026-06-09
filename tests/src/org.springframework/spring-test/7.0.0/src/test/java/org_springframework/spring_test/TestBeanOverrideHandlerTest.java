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
    void invokesTestBeanFactoryMethodWhenPreparingSpringTestContext() throws Exception {
        TestContextManager manager = new TestContextManager(UsesTestBean.class);
        UsesTestBean testInstance = new UsesTestBean();

        try {
            manager.prepareTestInstance(testInstance);

            ApplicationContext applicationContext = manager.getTestContext().getApplicationContext();
            MessageService bean = applicationContext.getBean(MessageService.class);

            assertThat(testInstance.messageService).isSameAs(bean);
            assertThat(bean.message()).isEqualTo("test override");
        } finally {
            manager.afterTestClass();
        }
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class UsesTestBean {
        @TestBean(methodName = "overrideMessageService")
        private MessageService messageService;

        private static MessageService overrideMessageService() {
            return new MessageService("test override");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        MessageService messageService() {
            return new MessageService("production");
        }
    }

    record MessageService(String message) {
    }
}
