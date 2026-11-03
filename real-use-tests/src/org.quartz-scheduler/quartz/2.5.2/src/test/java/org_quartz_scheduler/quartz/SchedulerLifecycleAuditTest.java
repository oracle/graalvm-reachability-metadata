/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerListener;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's scheduler listener subsystem for a report scheduler lifecycle.
 * An operations service records scheduling and shutdown events while a one-shot report runs.
 */
public class SchedulerLifecycleAuditTest {

    @Test
    @Timeout(60)
    public void recordsReportSchedulerLifecycle() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "report-lifecycle-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        LifecycleAudit audit = new LifecycleAudit();
        scheduler.getListenerManager().addSchedulerListener(audit);
        JobKey jobKey = new JobKey("daily-report", "reports");
        TriggerKey triggerKey = new TriggerKey("daily-report-trigger", "reports");
        JobDetail report = JobBuilder.newJob(ReportJob.class)
                .withIdentity(jobKey)
                .build();

        try {
            scheduler.scheduleJob(report, TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .startNow()
                    .build());
            scheduler.start();

            assertTrue(audit.awaitTriggerFinalized(), "Quartz did not finalize the report trigger");
            assertTrue(audit.events().contains("job-scheduled:" + triggerKey));
            assertTrue(audit.events().contains("scheduler-starting"));
            assertTrue(audit.events().contains("scheduler-started"));
            assertTrue(audit.events().contains("trigger-finalized:" + triggerKey));
        } finally {
            scheduler.shutdown(true);
        }

        assertTrue(audit.awaitShutdown(), "Quartz did not report scheduler shutdown");
        assertEquals(List.of(
                "job-scheduled:" + triggerKey,
                "scheduler-starting",
                "scheduler-started",
                "trigger-finalized:" + triggerKey,
                "scheduler-shutting-down",
                "scheduler-shutdown"), audit.events());
    }

    public static final class ReportJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            context.setResult("report-completed");
        }
    }

    private static final class LifecycleAudit implements SchedulerListener {
        private final List<String> events = new ArrayList<>();
        private final CountDownLatch triggerFinalized = new CountDownLatch(1);
        private final CountDownLatch shutdown = new CountDownLatch(1);

        @Override
        public synchronized void jobScheduled(Trigger trigger) {
            record("job-scheduled:" + trigger.getKey());
        }

        @Override
        public synchronized void triggerFinalized(Trigger trigger) {
            record("trigger-finalized:" + trigger.getKey());
            triggerFinalized.countDown();
        }

        @Override
        public synchronized void schedulerStarting() {
            record("scheduler-starting");
        }

        @Override
        public synchronized void schedulerStarted() {
            record("scheduler-started");
        }

        @Override
        public synchronized void schedulerShuttingdown() {
            record("scheduler-shutting-down");
        }

        @Override
        public synchronized void schedulerShutdown() {
            record("scheduler-shutdown");
            shutdown.countDown();
        }

        private synchronized void record(String event) {
            events.add(event);
        }

        private boolean awaitTriggerFinalized() throws InterruptedException {
            return triggerFinalized.await(10, TimeUnit.SECONDS);
        }

        private boolean awaitShutdown() throws InterruptedException {
            return shutdown.await(10, TimeUnit.SECONDS);
        }

        private synchronized List<String> events() {
            return List.copyOf(events);
        }

        @Override
        public void jobUnscheduled(TriggerKey triggerKey) {
        }

        @Override
        public void triggerPaused(TriggerKey triggerKey) {
        }

        @Override
        public void triggersPaused(String triggerGroup) {
        }

        @Override
        public void triggerResumed(TriggerKey triggerKey) {
        }

        @Override
        public void triggersResumed(String triggerGroup) {
        }

        @Override
        public void jobAdded(JobDetail jobDetail) {
        }

        @Override
        public void jobDeleted(JobKey jobKey) {
        }

        @Override
        public void jobPaused(JobKey jobKey) {
        }

        @Override
        public void jobsPaused(String jobGroup) {
        }

        @Override
        public void jobResumed(JobKey jobKey) {
        }

        @Override
        public void jobsResumed(String jobGroup) {
        }

        @Override
        public void schedulerError(String msg, SchedulerException cause) {
        }

        @Override
        public void schedulerInStandbyMode() {
        }

        @Override
        public void schedulingDataCleared() {
        }
    }
}
