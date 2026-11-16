/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskExecutorConfigurationsTest {
    @Test
    void autoConfiguredExecutorAppliesPoolPropertiesAndTaskDecorator() {
        AtomicInteger decoratedTasks = new AtomicInteger();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withPropertyValues("spring.threads.virtual.enabled=false", "spring.task.execution.pool.core-size=1",
                        "spring.task.execution.pool.max-size=1", "spring.task.execution.pool.queue-capacity=4",
                        "spring.task.execution.thread-name-prefix=orders-worker-")
                .withBean(TaskDecorator.class, () -> task -> () -> {
                    decoratedTasks.incrementAndGet();
                    task.run();
                }).run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean("applicationTaskExecutor",
                            ThreadPoolTaskExecutor.class);
                    String workerName = executor.submit(() -> Thread.currentThread().getName())
                            .get(30, TimeUnit.SECONDS);
                    assertTrue(workerName.startsWith("orders-worker-"));
                    assertEquals(1, executor.getCorePoolSize());
                    assertEquals(1, executor.getMaxPoolSize());
                    assertEquals(1, decoratedTasks.get());
                });
    }
}
