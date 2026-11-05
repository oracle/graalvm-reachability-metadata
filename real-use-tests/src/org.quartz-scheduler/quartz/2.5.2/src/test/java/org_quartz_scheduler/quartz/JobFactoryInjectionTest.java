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
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's job factory subsystem for an application-managed report dependency.
 * A reporting service injects its report sink into a Quartz job and observes the published result.
 */
public class JobFactoryInjectionTest {

    @Test
    @Timeout(60)
    public void createsJobsWithApplicationManagedDependencies() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "factory-report-scheduler");

        ReportSink sink = new ReportSink();
        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        scheduler.setJobFactory(new ReportJobFactory(sink));
        JobKey reportKey = new JobKey("factory-report", "reports");

        try {
            scheduler.scheduleJob(
                    JobBuilder.newJob(ReportJob.class)
                            .withIdentity(reportKey)
                            .usingJobData("reportName", "inventory")
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity("factory-report-trigger", "reports")
                            .startNow()
                            .forJob(reportKey)
                            .build());
            scheduler.start();

            assertTrue(sink.await(), "Quartz did not run the factory-created report job");
            assertEquals("inventory:published", sink.result);
        } finally {
            scheduler.shutdown(true);
        }
    }

    private static final class ReportJobFactory implements JobFactory {
        private final ReportSink sink;

        private ReportJobFactory(ReportSink sink) {
            this.sink = sink;
        }

        @Override
        public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
            if (bundle.getJobDetail().getJobClass() != ReportJob.class) {
                throw new SchedulerException("Unexpected report job class");
            }
            return new ReportJob(sink);
        }
    }

    public static final class ReportJob implements Job {
        private final ReportSink sink;

        public ReportJob(ReportSink sink) {
            this.sink = sink;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            String reportName = context.getMergedJobDataMap().getString("reportName");
            sink.publish(reportName + ":published");
            context.setResult(sink.result);
        }
    }

    private static final class ReportSink {
        private final CountDownLatch published = new CountDownLatch(1);
        private volatile String result;

        private void publish(String value) {
            result = value;
            published.countDown();
        }

        private boolean await() throws InterruptedException {
            return published.await(10, TimeUnit.SECONDS);
        }
    }
}
