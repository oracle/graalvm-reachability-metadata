/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = TestBeanOverrideHandlerTest.EmptyConfiguration.class)
public class TestBeanOverrideHandlerTest {
    @TestBean
    GreetingService greetingService;

    @Test
    void invokesStaticFactoryMethodForTestBeanOverride() {
        assertThat(this.greetingService).isNotNull();
        assertThat(this.greetingService.message()).isEqualTo("override from factory method");
    }

    public static GreetingService greetingService() {
        return new GreetingService("override from factory method");
    }

    @Configuration(proxyBeanMethods = false)
    public static class EmptyConfiguration {
    }

    public static final class NoOpContextInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
        }
    }

    static final class GreetingService {
        private final String message;

        GreetingService(String message) {
            this.message = message;
        }

        String message() {
            return this.message;
        }
    }
}
