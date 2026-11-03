/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Set;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's non-concurrent job execution subsystem for a report worker.
 * A reporting service submits two instances of one job and observes serialized execution.
 */
public class NonConcurrentJobExecutionTest {

    @Test
    @Timeout(60)
    public void serializesConcurrentTriggersForOneReportJob() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "2");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "exclusive-report-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        ExecutionTracker tracker = new ExecutionTracker();
        JobDataMap data = new JobDataMap();
        data.put("tracker", tracker);
        JobDetail report = JobBuilder.newJob(ExclusiveReportJob.class)
                .withIdentity("exclusive-report", "reports")
                .usingJobData(data)
                .storeDurably()
                .build();
        Trigger first = TriggerBuilder.newTrigger()
                .withIdentity("exclusive-report-first", "reports")
                .forJob(report)
                .startNow()
                .build();
        Trigger second = TriggerBuilder.newTrigger()
                .withIdentity("exclusive-report-second", "reports")
                .forJob(report)
                .startNow()
                .build();

        try {
            scheduler.scheduleJob(report, Set.of(first, second), false);
            scheduler.start();

            assertTrue(tracker.firstStarted.await(10, TimeUnit.SECONDS),
                    "Quartz did not start the report job");
            tracker.releaseFirst.countDown();
            assertTrue(tracker.allCompleted.await(10, TimeUnit.SECONDS),
                    "Quartz did not complete both report executions");
            assertEquals(2, tracker.completed.get());
            assertEquals(1, tracker.maximumRunning.get());
        } finally {
            scheduler.shutdown(true);
        }
    }

    @DisallowConcurrentExecution
    public static final class ExclusiveReportJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            ExecutionTracker tracker = (ExecutionTracker) context.getMergedJobDataMap().get("tracker");
            tracker.runReport();
        }
    }

    private static final class ExecutionTracker {
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger maximumRunning = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final CountDownLatch allCompleted = new CountDownLatch(2);

        private void runReport() {
            int current = running.incrementAndGet();
            maximumRunning.accumulateAndGet(current, Math::max);
            firstStarted.countDown();
            try {
                if (completed.get() == 0 && !awaitRelease()) {
                    throw new IllegalStateException("Timed out waiting to release report");
                }
            } finally {
                running.decrementAndGet();
                completed.incrementAndGet();
                allCompleted.countDown();
            }
        }

        private boolean awaitRelease() {
            try {
                return releaseFirst.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
