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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(TestBeanOverrideHandlerTest.TestConfiguration.class)
public class TestBeanOverrideHandlerTest {
    @TestBean
    private Greeting greeting;

    private static Greeting greeting() {
        return new Greeting("test greeting");
    }

    @Test
    void replacesApplicationBeanWithFactoryMethodResult(ApplicationContext context) {
        assertThat(this.greeting.message()).isEqualTo("test greeting");
        assertThat(context.getBean(Greeting.class)).isSameAs(this.greeting);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        Greeting applicationGreeting() {
            return new Greeting("application greeting");
        }
    }

    record Greeting(String message) {
    }
}
