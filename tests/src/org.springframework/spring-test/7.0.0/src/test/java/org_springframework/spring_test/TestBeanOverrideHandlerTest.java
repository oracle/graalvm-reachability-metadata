/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    private static final AtomicInteger FACTORY_INVOCATIONS = new AtomicInteger();

    private static final GreetingService TEST_GREETING_SERVICE = new TestGreetingService();

    @Test
    void invokesTestBeanFactoryMethodToCreateOverrideInstance() throws Exception {
        FACTORY_INVOCATIONS.set(0);
        TestBeanTestCase testInstance = new TestBeanTestCase();
        TestContextManager manager = new TestContextManager(TestBeanTestCase.class);

        manager.prepareTestInstance(testInstance);

        assertThat(FACTORY_INVOCATIONS).hasValue(1);
        assertThat(testInstance.greetingService).isSameAs(TEST_GREETING_SERVICE);
        assertThat(testInstance.greetingClient.greet()).isEqualTo("test greeting");
    }

    public static GreetingService createTestGreetingService() {
        FACTORY_INVOCATIONS.incrementAndGet();
        return TEST_GREETING_SERVICE;
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    public static class TestBeanTestCase {
        @TestBean(methodName = "org_springframework.spring_test.TestBeanOverrideHandlerTest"
                + "#createTestGreetingService")
        private GreetingService greetingService;

        @Autowired
        private GreetingClient greetingClient;
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestConfiguration {
        @Bean
        GreetingService greetingService() {
            return new ProductionGreetingService();
        }

        @Bean
        GreetingClient greetingClient(GreetingService greetingService) {
            return new GreetingClient(greetingService);
        }
    }

    interface GreetingService {
        String greet();
    }

    static class ProductionGreetingService implements GreetingService {
        @Override
        public String greet() {
            return "production greeting";
        }
    }

    static class TestGreetingService implements GreetingService {
        @Override
        public String greet() {
            return "test greeting";
        }
    }

    static class GreetingClient {
        private final GreetingService greetingService;

        GreetingClient(GreetingService greetingService) {
            this.greetingService = greetingService;
        }

        String greet() {
            return this.greetingService.greet();
        }
    }
}
