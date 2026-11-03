/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.InterruptableJob;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.plugins.interrupt.JobInterruptMonitorPlugin;
import org.quartz.simpl.SimpleThreadPool;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's job interruption plugin subsystem for a bounded report task.
 * A reporting service interrupts a task that exceeds its configured runtime limit.
 */
public class JobInterruptMonitorPluginTest {

    @Test
    @Timeout(60)
    public void interruptsReportThatExceedsRuntimeLimit() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "interrupt-monitor-scheduler");
        properties.setProperty("org.quartz.plugin.jobInterrupt.class", JobInterruptMonitorPlugin.class.getName());

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        JobKey jobKey = new JobKey("slow-report", "reports");

        try {
            scheduler.scheduleJob(JobBuilder.newJob(InterruptibleReportJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(JobInterruptMonitorPlugin.AUTO_INTERRUPTIBLE, true)
                    .usingJobData(JobInterruptMonitorPlugin.MAX_RUN_TIME, 100L)
                    .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity("slow-report-trigger", "reports")
                            .startNow()
                            .forJob(jobKey)
                            .build());
            scheduler.start();

            assertTrue(InterruptibleReportJob.started.await(10, TimeUnit.SECONDS),
                    "Quartz did not start the report");
            assertTrue(InterruptibleReportJob.interrupted.await(10, TimeUnit.SECONDS),
                    "Quartz did not interrupt the overlong report");
        } finally {
            scheduler.shutdown(true);
        }
    }

    public static final class InterruptibleReportJob implements InterruptableJob {
        private static final CountDownLatch started = new CountDownLatch(1);
        private static final CountDownLatch interrupted = new CountDownLatch(1);
        private volatile boolean interruptRequested;

        @Override
        public void execute(JobExecutionContext context) {
            started.countDown();
            while (!interruptRequested) {
                Thread.yield();
            }
        }

        @Override
        public void interrupt() {
            interruptRequested = true;
            interrupted.countDown();
        }
    }
}
