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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void invokesFactoryMethodToCreateTestBeanOverride() throws Exception {
        TestBeanOverrideTestCase.resetFactoryInvocations();
        TestBeanOverrideTestCase testInstance = new TestBeanOverrideTestCase();
        TestContextManager testContextManager = new TestContextManager(TestBeanOverrideTestCase.class);

        testContextManager.prepareTestInstance(testInstance);

        ExampleService serviceFromContext = testContextManager.getTestContext()
                .getApplicationContext()
                .getBean(ExampleService.class);
        assertThat(testInstance.service).isSameAs(serviceFromContext);
        assertThat(serviceFromContext.message()).isEqualTo("test override");
        assertThat(TestBeanOverrideTestCase.factoryInvocations()).isEqualTo(1);
    }

    @ContextConfiguration(classes = ExampleConfiguration.class)
    public static class TestBeanOverrideTestCase {
        private static int factoryInvocations;

        @TestBean
        private ExampleService service;

        private static ExampleService service() {
            factoryInvocations++;
            return new ExampleService("test override");
        }

        static void resetFactoryInvocations() {
            factoryInvocations = 0;
        }

        static int factoryInvocations() {
            return factoryInvocations;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExampleConfiguration {
        @Bean
        ExampleService service() {
            return new ExampleService("production");
        }
    }

    record ExampleService(String message) {
    }
}
