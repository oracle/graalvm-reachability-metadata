/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    @Test
    void testBeanOverrideInvokesFactoryMethodAndInjectsOverride() throws Exception {
        TestBeanTestCase testInstance = new TestBeanTestCase();
        TestContextManager manager = new TestContextManager(TestBeanTestCase.class);

        try {
            manager.prepareTestInstance(testInstance);

            assertThat(testInstance.service).isNotNull();
            assertThat(testInstance.service.message()).isEqualTo("override");
            assertThat(testInstance.autowiredService).isSameAs(testInstance.service);
        }
        finally {
            manager.getTestContext().markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
        }
    }

    @ContextConfiguration(classes = TestBeanConfiguration.class)
    static class TestBeanTestCase {
        @TestBean
        private GreetingService service;

        @Autowired
        private GreetingService autowiredService;

        private static GreetingService service() {
            return new TestGreetingService("override");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeanConfiguration {
        @Bean
        GreetingService service() {
            return new TestGreetingService("original");
        }
    }

    public static class NoOpApplicationContextInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
        }
    }

    interface GreetingService {
        String message();
    }

    static class TestGreetingService implements GreetingService {
        private final String message;

        TestGreetingService(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return this.message;
        }
    }
}
