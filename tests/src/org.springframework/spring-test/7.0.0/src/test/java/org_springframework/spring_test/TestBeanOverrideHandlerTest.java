/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void invokesTestBeanFactoryMethodWhenPreparingTestContext() throws Exception {
        TestBeanTestCase testInstance = new TestBeanTestCase();

        new TestContextManager(TestBeanTestCase.class).prepareTestInstance(testInstance);

        assertThat(testInstance.messageService.message()).isEqualTo("override");
        assertThat(testInstance.messageClient.message()).isEqualTo("override");
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class TestBeanTestCase {
        @TestBean
        private MessageService messageService;

        @Autowired
        private MessageClient messageClient;

        private static MessageService messageService() {
            return () -> "override";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        MessageService messageService() {
            return () -> "original";
        }

        @Bean
        MessageClient messageClient(MessageService messageService) {
            return new MessageClient(messageService);
        }
    }

    interface MessageService {
        String message();
    }

    static class MessageClient {
        private final MessageService messageService;

        MessageClient(MessageService messageService) {
            this.messageService = messageService;
        }

        String message() {
            return this.messageService.message();
        }
    }
}
