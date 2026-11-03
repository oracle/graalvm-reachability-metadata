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
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's scheduler context subsystem for a tenant-aware order dispatch.
 * An order service puts deployment settings in the scheduler context and observes a job using them.
 */
public class SchedulerContextDispatchTest {

    @Test
    @Timeout(60)
    public void dispatchesOrderUsingDeploymentContext() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "order-dispatch-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        SchedulerContext schedulerContext = scheduler.getContext();
        schedulerContext.put("deployment", "warehouse-eu");
        schedulerContext.put("dispatchMode", "batched");
        RecordingListener listener = new RecordingListener();
        scheduler.getListenerManager().addJobListener(listener);

        try {
            scheduler.scheduleJob(
                    JobBuilder.newJob(OrderDispatchJob.class)
                            .withIdentity("dispatch-order", "orders")
                            .usingJobData("orderId", "order-482")
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity("dispatch-order-trigger", "orders")
                            .startNow()
                            .build());
            scheduler.start();

            assertTrue(listener.await(), "Quartz did not dispatch the order");
            assertEquals("warehouse-eu:batched:order-482", listener.result);
            assertNull(listener.failure);
            assertEquals("warehouse-eu", schedulerContext.getString("deployment"));
            assertEquals("batched", schedulerContext.getString("dispatchMode"));
        } finally {
            scheduler.shutdown(true);
        }
    }

    public static final class OrderDispatchJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                SchedulerContext schedulerContext = context.getScheduler().getContext();
                String deployment = schedulerContext.getString("deployment");
                String dispatchMode = schedulerContext.getString("dispatchMode");
                String orderId = context.getMergedJobDataMap().getString("orderId");
                context.setResult(deployment + ":" + dispatchMode + ":" + orderId);
            } catch (Exception exception) {
                throw new JobExecutionException(exception);
            }
        }
    }

    private static final class RecordingListener implements JobListener {
        private final CountDownLatch execution = new CountDownLatch(1);
        private volatile String result;
        private volatile JobExecutionException failure;

        @Override
        public String getName() {
            return "order-dispatch-recording-listener";
        }

        @Override
        public void jobToBeExecuted(JobExecutionContext context) {
        }

        @Override
        public void jobExecutionVetoed(JobExecutionContext context) {
        }

        @Override
        public void jobWasExecuted(JobExecutionContext context, JobExecutionException exception) {
            result = (String) context.getResult();
            failure = exception;
            execution.countDown();
        }

        private boolean await() throws InterruptedException {
            return execution.await(10, TimeUnit.SECONDS);
        }
    }
}
