/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AutoConfigureOrderTest {
    @Test
    void earlierAutoConfigurationSuppliesTheDefaultBeforeLaterOne() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(Later.class, Earlier.class))
                .run(context -> {
                    assertEquals("early", context.getBean(CharSequence.class));
                    assertFalse(context.containsBean("laterValue"));
                });
    }

    @AutoConfiguration
    @AutoConfigureOrder(-100)
    public static class Earlier {
        @Bean
        @ConditionalOnMissingBean(CharSequence.class)
        public CharSequence earlierValue() {
            return "early";
        }
    }

    @AutoConfiguration
    @AutoConfigureOrder(100)
    public static class Later {
        @Bean
        @ConditionalOnMissingBean(CharSequence.class)
        public CharSequence laterValue() {
            return "late";
        }
    }
}
