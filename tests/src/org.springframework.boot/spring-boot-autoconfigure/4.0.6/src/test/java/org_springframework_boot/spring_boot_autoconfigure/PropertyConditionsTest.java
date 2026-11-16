/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PropertyConditionsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Features.class);

    @Test
    void booleanPropertyEnablesOnlyTheTrueBranch() {
        runner.withPropertyValues("sample.enabled=true").run(context ->
                assertEquals("enabled", context.getBean("booleanFeature")));
        runner.withPropertyValues("sample.enabled=false").run(context ->
                assertFalse(context.containsBean("booleanFeature")));
        runner.run(context -> assertFalse(context.containsBean("booleanFeature")));
    }

    @Test
    void namedPropertyRequiresItsConfiguredValue() {
        runner.withPropertyValues("sample.mode=active").run(context ->
                assertEquals("active", context.getBean("propertyFeature")));
        runner.withPropertyValues("sample.mode=passive").run(context ->
                assertFalse(context.containsBean("propertyFeature")));
    }

    @Test
    void expressionEvaluatesConfiguredThreshold() {
        runner.withPropertyValues("sample.capacity=8").run(context ->
                assertEquals("capacity available", context.getBean("expressionFeature")));
        runner.withPropertyValues("sample.capacity=1").run(context ->
                assertFalse(context.containsBean("expressionFeature")));
    }

    @Test
    void threadingConditionSelectsTheConfiguredThreadingModel() {
        runner.withPropertyValues("spring.threads.virtual.enabled=false").run(context -> {
            assertEquals("platform", context.getBean("platformFeature"));
            assertFalse(context.containsBean("virtualFeature"));
        });
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            assertEquals("virtual", context.getBean("virtualFeature"));
            assertFalse(context.containsBean("platformFeature"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    public static class Features {
        @Bean
        @ConditionalOnBooleanProperty(prefix = "sample", name = "enabled")
        public String booleanFeature() {
            return "enabled";
        }

        @Bean
        @ConditionalOnProperty(prefix = "sample", name = "mode", havingValue = "active")
        public String propertyFeature() {
            return "active";
        }

        @Bean
        @ConditionalOnExpression("${sample.capacity:0} >= 4")
        public String expressionFeature() {
            return "capacity available";
        }

        @Bean
        @ConditionalOnThreading(Threading.PLATFORM)
        public String platformFeature() {
            return "platform";
        }

        @Bean
        @ConditionalOnThreading(Threading.VIRTUAL)
        public String virtualFeature() {
            return "virtual";
        }
    }
}
