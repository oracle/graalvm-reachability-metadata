/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduledBeanLazyInitializationExcludeFilterTest {
    @Test
    void scheduledBeansAreExcludedFromLazyInitializationButOrdinaryBeansAreNot() {
        new ApplicationContextRunner().withUserConfiguration(Scheduling.class)
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class)).run(context -> {
                    LazyInitializationExcludeFilter filter = context.getBean(
                            "scheduledBeanLazyInitializationExcludeFilter", LazyInitializationExcludeFilter.class);
                    assertTrue(filter.isExcluded("scheduledWork", new RootBeanDefinition(ScheduledWork.class),
                            ScheduledWork.class));
                    assertFalse(filter.isExcluded("ordinaryWork", new RootBeanDefinition(OrdinaryWork.class),
                            OrdinaryWork.class));
                    assertFalse(filter.isExcluded("ordinaryWork", new RootBeanDefinition(OrdinaryWork.class),
                            OrdinaryWork.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Scheduling {
    }

    public static class ScheduledWork {
        @Scheduled(fixedDelay = 60000)
        public void refresh() {
        }
    }

    public static class OrdinaryWork {
        public void refresh() {
        }
    }
}
