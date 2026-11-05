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
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's persistent job-data subsystem for a recurring batch workflow.
 * A batch job stores its completed-run count in its job data and observes that count across executions.
 */
public class PersistentJobDataWorkflowTest {

    @Test
    @Timeout(60)
    public void carriesBatchProgressAcrossRecurringExecutions() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "persistent-batch-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        JobKey batchKey = new JobKey("inventory-batch", "operations");
        RecordingListener listener = new RecordingListener(2);
        scheduler.getListenerManager().addJobListener(listener);
        JobDetail batch = JobBuilder.newJob(InventoryBatchJob.class)
                .withIdentity(batchKey)
                .usingJobData("completedRuns", 0)
                .storeDurably()
                .build();

        try {
            scheduler.scheduleJob(batch, TriggerBuilder.newTrigger()
                    .withIdentity("inventory-batch-trigger", "operations")
                    .forJob(batchKey)
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(10)
                            .withRepeatCount(1))
                    .build());
            scheduler.start();

            assertTrue(listener.await(), "Quartz did not complete both inventory batches");
            assertEquals(List.of("completed-run-1", "completed-run-2"), listener.results());
            assertEquals(2, scheduler.getJobDetail(batchKey).getJobDataMap().getInt("completedRuns"));
        } finally {
            scheduler.shutdown(true);
        }
    }

    @PersistJobDataAfterExecution
    public static final class InventoryBatchJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            JobDataMap data = context.getJobDetail().getJobDataMap();
            int completedRuns = data.getInt("completedRuns") + 1;
            data.put("completedRuns", completedRuns);
            context.setResult("completed-run-" + completedRuns);
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
            return "persistent-batch-recording-listener";
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
