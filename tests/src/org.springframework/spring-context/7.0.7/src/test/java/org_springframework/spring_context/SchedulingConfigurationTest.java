/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SchedulingConfigurationTest {
    @Test
    void registersAndExecutesBothRepeatableSchedules() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SchedulingConfiguration.class)) {
            ScheduledAnnotationBeanPostProcessor processor = context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertEquals(2, processor.getScheduledTasks().size());
            for (ScheduledTask task : processor.getScheduledTasks()) {
                task.getTask().getRunnable().run();
            }
            assertEquals(2, context.getBean(RefreshJob.class).executions);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class SchedulingConfiguration {
        @Bean
        public RefreshJob refreshJob() {
            return new RefreshJob();
        }

        @Bean
        public ThreadPoolTaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }
    }

    public static class RefreshJob {
        private int executions;

        @Schedules({
                @Scheduled(fixedRate = 3600000, initialDelay = 3600000),
                @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
        })
        public void refresh() {
            executions++;
        }
    }
}
