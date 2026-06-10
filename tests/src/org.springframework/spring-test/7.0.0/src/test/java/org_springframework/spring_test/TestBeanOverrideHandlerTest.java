/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestBeanOverrideHandlerTest.Config.class)
public class TestBeanOverrideHandlerTest {
    @TestBean
    private GreetingService greetingService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void invokesFactoryMethodToCreateOverrideInstance() {
        assertThat(this.greetingService.message()).isEqualTo("override");
        assertThat(this.applicationContext.getBean(GreetingService.class)).isSameAs(this.greetingService);
    }

    private static GreetingService greetingService() {
        return new GreetingService("override");
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        GreetingService greetingService() {
            return new GreetingService("original");
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
