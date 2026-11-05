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
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's job scheduling and listener notification subsystem for a recurring report.
 * A reporting service schedules two executions and records the results delivered to its listener.
 */
public class ScheduledReportWorkflowTest {

    @Test
    @Timeout(60)
    public void schedulesRecurringReportAndPublishesExecutionResults() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "2");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "report-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        RecordingListener listener = new RecordingListener(2);
        scheduler.getListenerManager().addJobListener(listener);

        JobDetail report = JobBuilder.newJob(ReportJob.class)
                .withIdentity("inventory-report", "reports")
                .usingJobData("reportName", "inventory")
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("inventory-report-trigger", "reports")
                .forJob(report)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(25)
                        .withRepeatCount(1))
                .build();

        try {
            scheduler.start();
            scheduler.scheduleJob(report, trigger);

            assertTrue(listener.await(), "Quartz did not complete the scheduled report executions");
            assertEquals(List.of("inventory:completed", "inventory:completed"), listener.results());
            assertFalse(scheduler.checkExists(report.getKey()));
            assertFalse(scheduler.checkExists(trigger.getKey()));
            assertFalse(scheduler.isShutdown());
        } finally {
            scheduler.shutdown(true);
        }
    }

    public static final class ReportJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            String reportName = context.getMergedJobDataMap().getString("reportName");
            context.setResult(reportName + ":completed");
        }
    }

    private static final class RecordingListener implements JobListener {
        private final CountDownLatch executions;
        private final List<String> results = new CopyOnWriteArrayList<>();

        private RecordingListener(int expectedExecutions) {
            executions = new CountDownLatch(expectedExecutions);
        }

        @Override
        public String getName() {
            return "report-recording-listener";
        }

        @Override
        public void jobToBeExecuted(JobExecutionContext context) {
        }

        @Override
        public void jobExecutionVetoed(JobExecutionContext context) {
        }

        @Override
        public void jobWasExecuted(JobExecutionContext context, JobExecutionException exception) {
            if (exception == null) {
                results.add((String) context.getResult());
            }
            executions.countDown();
        }

        private boolean await() throws InterruptedException {
            return executions.await(10, TimeUnit.SECONDS);
        }

        private List<String> results() {
            return List.copyOf(results);
        }
    }
}
