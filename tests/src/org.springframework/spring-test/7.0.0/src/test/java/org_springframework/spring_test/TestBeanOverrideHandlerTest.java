/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    private static final String FACTORY_METHOD =
            "org_springframework.spring_test.TestBeanOverrideHandlerTest$ExternalFactory#overrideMessageService";

    @Test
    void invokesTestBeanFactoryMethodWhenPreparingTestInstance() throws Exception {
        ExternalFactory.resetFactoryInvocations();
        BeanOverrideTestCase testInstance = new BeanOverrideTestCase();
        TestContextManager testContextManager = new TestContextManager(BeanOverrideTestCase.class);

        testContextManager.prepareTestInstance(testInstance);

        assertThat(testInstance.messageService).isNotNull();
        assertThat(testInstance.messageService.message()).isEqualTo("override");
        assertThat(testContextManager.getTestContext().getApplicationContext().getBean(MessageService.class))
                .isSameAs(testInstance.messageService);
        assertThat(ExternalFactory.factoryInvocations()).isEqualTo(1);

        testContextManager.getTestContext().markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class BeanOverrideTestCase {
        @TestBean(methodName = FACTORY_METHOD)
        MessageService messageService;
    }

    public static class ExternalFactory {
        private static int factoryInvocations;

        public static MessageService overrideMessageService() {
            factoryInvocations++;
            return () -> "override";
        }

        static void resetFactoryInvocations() {
            factoryInvocations = 0;
        }

        static int factoryInvocations() {
            return factoryInvocations;
        }
    }

    interface MessageService {
        String message();
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        MessageService messageService() {
            return () -> "original";
        }
    }
}
