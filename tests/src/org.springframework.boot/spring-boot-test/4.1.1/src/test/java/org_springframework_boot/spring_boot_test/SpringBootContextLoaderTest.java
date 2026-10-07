/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestContextManager;

public class SpringBootContextLoaderTest {
    @Test
    void loadsThroughAStringArrayMainMethod() {
        try (ConfigurableApplicationContext context = load(ArgumentsMainFixture.class)) {
            assertThat(ArgumentsMainApplication.invocations).isEqualTo(1);
            assertThat(context.getBean("launchMode", String.class)).isEqualTo("arguments");
        }
    }

    @Test
    void loadsThroughANoArgumentsMainMethod() {
        try (ConfigurableApplicationContext context = load(NoArgumentsMainFixture.class)) {
            assertThat(NoArgumentsMainApplication.invocations).isEqualTo(1);
            assertThat(context.getBean("launchMode", String.class)).isEqualTo("no-arguments");
        }
    }

    private ConfigurableApplicationContext load(Class<?> testClass) {
        TestContextManager manager = new TestContextManager(testClass);
        return (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext();
    }

    @SpringBootTest(
            classes = ArgumentsMainApplication.class,
            useMainMethod = UseMainMethod.ALWAYS,
            webEnvironment = SpringBootTest.WebEnvironment.NONE)
    static class ArgumentsMainFixture {}

    @SpringBootConfiguration(proxyBeanMethods = false)
    public static class ArgumentsMainApplication {
        static int invocations;

        public static void main(String[] args) {
            invocations++;
            SpringApplication.run(ArgumentsMainApplication.class, args);
        }

        @Bean
        String launchMode() {
            return "arguments";
        }
    }

    @SpringBootTest(
            classes = NoArgumentsMainApplication.class,
            useMainMethod = UseMainMethod.ALWAYS,
            webEnvironment = SpringBootTest.WebEnvironment.NONE)
    static class NoArgumentsMainFixture {}

    @SpringBootConfiguration(proxyBeanMethods = false)
    public static class NoArgumentsMainApplication {
        static int invocations;

        public static void main() {
            invocations++;
            SpringApplication.run(NoArgumentsMainApplication.class);
        }

        @Bean
        String launchMode() {
            return "no-arguments";
        }
    }
}
