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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestBeanOverrideHandlerTest.TestConfiguration.class)
@DirtiesContext
public class TestBeanOverrideHandlerTest {
    private static final TestService OVERRIDE = new TestService("override");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TestService autowiredService;

    @TestBean(name = "testService", methodName = "testService")
    private TestService overriddenService;

    @Test
    void invokesTestBeanFactoryMethodToCreateOverrideInstance() {
        assertThat(this.overriddenService).isSameAs(OVERRIDE);
        assertThat(this.overriddenService.name()).isEqualTo("override");
        assertThat(this.autowiredService).isSameAs(this.overriddenService);
        assertThat(this.applicationContext.getBean("testService", TestService.class))
                .isSameAs(this.overriddenService);
    }

    private static TestService testService() {
        return OVERRIDE;
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        TestService testService() {
            return new TestService("original");
        }
    }

    static final class TestService {
        private final String name;

        TestService(String name) {
            this.name = name;
        }

        String name() {
            return this.name;
        }
    }
}
