/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.thread.TaskRunner;
import org.apache.activemq.thread.TaskRunnerFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskRunnerFactoryTest {

    @Test
    void executesRunnableAndTaskWithVirtualThreads() throws Exception {
        TaskRunnerFactory factory = new TaskRunnerFactory(
                "virtual-runner", Thread.NORM_PRIORITY, true, 100, false, true);
        factory.setThreadClassLoader(TaskRunnerFactory.class.getClassLoader());
        factory.setShutdownAwaitTermination(10000);
        CountDownLatch runnableExecuted = new CountDownLatch(1);

        try {
            factory.execute(runnableExecuted::countDown);
            assertThat(runnableExecuted.await(10, TimeUnit.SECONDS)).isTrue();

            AtomicInteger iterations = new AtomicInteger();
            CountDownLatch taskExecuted = new CountDownLatch(1);
            TaskRunner runner = factory.createTaskRunner(() -> {
                iterations.incrementAndGet();
                taskExecuted.countDown();
                return false;
            }, "virtual-task");
            try {
                runner.wakeup();
                assertThat(taskExecuted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(iterations).hasValue(1);
            } finally {
                runner.shutdown(10000);
            }
        } finally {
            factory.shutdownGraceful();
        }
    }
}
