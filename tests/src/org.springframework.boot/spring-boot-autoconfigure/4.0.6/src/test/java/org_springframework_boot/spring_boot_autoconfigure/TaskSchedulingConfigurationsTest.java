/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskSchedulingConfigurationsTest {
    @Test
    void schedulerUsesConfiguredPoolAndDecoratesScheduledWork() {
        AtomicInteger decoratedTasks = new AtomicInteger();
        new ApplicationContextRunner().withUserConfiguration(Scheduling.class)
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withPropertyValues("spring.threads.virtual.enabled=false", "spring.task.scheduling.pool.size=2",
                        "spring.task.scheduling.thread-name-prefix=orders-scheduler-")
                .withBean(TaskDecorator.class, () -> task -> () -> {
                    decoratedTasks.incrementAndGet();
                    task.run();
                }).run(context -> {
                    ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
                    CompletableFuture<String> threadName = new CompletableFuture<>();
                    ScheduledFuture<?> scheduled = scheduler.schedule(
                            () -> threadName.complete(Thread.currentThread().getName()), Instant.now());
                    assertNotNull(scheduled);
                    assertTrue(threadName.get(30, TimeUnit.SECONDS).startsWith("orders-scheduler-"));
                    scheduled.get(30, TimeUnit.SECONDS);
                    assertEquals(2, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
                    assertEquals(1, decoratedTasks.get());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Scheduling {
    }
}
