/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestContextAotGenerator;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBeanOverrideHandlerTest {
    private static final AtomicInteger factoryMethodInvocations = new AtomicInteger();

    @Test
    void invokesTestBeanFactoryMethodWhileProcessingBeanOverrideAheadOfTime() {
        RuntimeHints runtimeHints = new RuntimeHints();
        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
        TestContextAotGenerator generator =
                new TestContextAotGenerator(generatedFiles, runtimeHints);
        factoryMethodInvocations.set(0);

        try {
            generator.processAheadOfTime(Stream.of(BeanOverrideTestCase.class));

            assertThat(factoryMethodInvocations.get()).isPositive();
        } catch (IllegalStateException ex) {
            assertThat(ex)
                    .hasMessage("Cannot perform AOT processing during AOT run-time execution");
        }
    }

    @ContextConfiguration(classes = TestConfiguration.class)
    static class BeanOverrideTestCase {
        @TestBean(methodName = "testGreetingService")
        GreetingService greetingService;

        private static GreetingService testGreetingService() {
            factoryMethodInvocations.incrementAndGet();
            return new TestGreetingService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestConfiguration {
        @Bean
        public GreetingService greetingService() {
            return () -> "production";
        }
    }

    interface GreetingService {
        String greeting();
    }

    static class TestGreetingService implements GreetingService {
        @Override
        public String greeting() {
            return "test";
        }
    }
}
