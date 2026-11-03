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
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleClassLoadHelper;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.xml.XMLSchedulingDataProcessor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's XML scheduling data subsystem for an operations report.
 * An application loads a deployment-owned schedule document and observes its configured job run.
 */
public class XmlSchedulingDataTest {

    @Test
    @Timeout(60)
    public void loadsScheduleDocumentAndRunsConfiguredJob() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "xml-report-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        RecordingListener listener = new RecordingListener();
        scheduler.getListenerManager().addJobListener(listener);

        String schedule = """
                <?xml version="1.0" encoding="UTF-8"?>
                <job-scheduling-data
                        xmlns="http://www.quartz-scheduler.org/xml/JobSchedulingData"
                        version="2.0">
                    <schedule>
                        <job>
                            <name>daily-operations-report</name>
                            <group>operations</group>
                            <job-class>org_quartz_scheduler.quartz.XmlSchedulingDataTest$ReportJob</job-class>
                            <job-data-map>
                                <entry><key>reportName</key><value>warehouse</value></entry>
                            </job-data-map>
                        </job>
                        <trigger>
                            <simple>
                                <name>daily-operations-report-trigger</name>
                                <group>operations</group>
                                <job-name>daily-operations-report</job-name>
                                <job-group>operations</job-group>
                                <start-time-seconds-in-future>0</start-time-seconds-in-future>
                                <repeat-count>0</repeat-count>
                                <repeat-interval>1</repeat-interval>
                            </simple>
                        </trigger>
                    </schedule>
                </job-scheduling-data>
                """;

        try {
            XMLSchedulingDataProcessor processor = new XMLSchedulingDataProcessor(new SimpleClassLoadHelper());
            processor.processStreamAndScheduleJobs(
                    new ByteArrayInputStream(schedule.getBytes(StandardCharsets.UTF_8)),
                    "deployment-schedule.xml",
                    scheduler);
            assertTrue(scheduler.checkExists(new JobKey("daily-operations-report", "operations")));

            scheduler.start();
            assertTrue(listener.await(), "Quartz did not run the job loaded from XML");
            assertEquals("warehouse:completed", listener.result);
            assertNull(listener.failure);
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
        private final CountDownLatch execution = new CountDownLatch(1);
        private volatile String result;
        private volatile JobExecutionException failure;

        @Override
        public String getName() {
            return "xml-report-recording-listener";
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
