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
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.TriggerListener;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's trigger listener subsystem for a maintenance policy on a billing job.
 * A billing service vetoes a protected trigger while maintenance is active and observes that no job runs.
 */
public class TriggerListenerPolicyTest {

    @Test
    @Timeout(60)
    public void vetoesProtectedBillingTriggerDuringMaintenance() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "billing-policy-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        TriggerKey triggerKey = new TriggerKey("billing-trigger", "billing");
        ExecutionRecord record = new ExecutionRecord();
        MaintenancePolicy policy = new MaintenancePolicy(triggerKey);
        scheduler.getListenerManager().addTriggerListener(policy, KeyMatcher.keyEquals(triggerKey));

        try {
            scheduler.scheduleJob(
                    JobBuilder.newJob(BillingJob.class)
                            .withIdentity("billing-job", "billing")
                            .usingJobData(new JobDataMap(Map.of("record", record)))
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey)
                            .startNow()
                            .build());
            scheduler.start();

            assertTrue(policy.awaitVeto(), "Quartz did not ask the maintenance policy about the trigger");
            assertEquals(triggerKey, policy.firedTrigger);
            assertEquals(0, record.executions.get());
        } finally {
            scheduler.shutdown(true);
        }
    }

    private static final class MaintenancePolicy implements TriggerListener {
        private final TriggerKey protectedTrigger;
        private final CountDownLatch vetoed = new CountDownLatch(1);
        private volatile TriggerKey firedTrigger;

        private MaintenancePolicy(TriggerKey protectedTrigger) {
            this.protectedTrigger = protectedTrigger;
        }

        @Override
        public String getName() {
            return "billing-maintenance-policy";
        }

        @Override
        public void triggerFired(Trigger trigger, JobExecutionContext context) {
            firedTrigger = trigger.getKey();
        }

        @Override
        public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
            boolean shouldVeto = protectedTrigger.equals(trigger.getKey());
            if (shouldVeto) {
                vetoed.countDown();
            }
            return shouldVeto;
        }

        @Override
        public void triggerMisfired(Trigger trigger) {
        }

        @Override
        public void triggerComplete(
                Trigger trigger,
                JobExecutionContext context,
                Trigger.CompletedExecutionInstruction triggerInstruction) {
        }

        private boolean awaitVeto() throws InterruptedException {
            return vetoed.await(10, TimeUnit.SECONDS);
        }
    }

    private static final class ExecutionRecord {
        private final AtomicInteger executions = new AtomicInteger();
    }

    public static final class BillingJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            ExecutionRecord record = (ExecutionRecord) context.getMergedJobDataMap().get("record");
            record.executions.incrementAndGet();
        }
    }
}
