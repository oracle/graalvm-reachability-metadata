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
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.JobChainingJobListener;
import org.quartz.simpl.SimpleThreadPool;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's job-chaining listener subsystem for a staged report workflow.
 * A reporting service chains enrichment after collection and observes both completed stages.
 */
public class JobChainingWorkflowTest {

    @Test
    @Timeout(60)
    public void chainsEnrichmentAfterCollection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "chained-report-scheduler");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        JobKey collectionKey = new JobKey("collect-report", "reports");
        JobKey enrichmentKey = new JobKey("enrich-report", "reports");
        ExecutionLog log = new ExecutionLog();
        JobDetail collection = JobBuilder.newJob(CollectionJob.class)
                .withIdentity(collectionKey)
                .usingJobData(log.asData())
                .build();
        JobDetail enrichment = JobBuilder.newJob(EnrichmentJob.class)
                .withIdentity(enrichmentKey)
                .usingJobData(log.asData())
                .storeDurably()
                .build();
        JobChainingJobListener chaining = new JobChainingJobListener("report-stage-chaining");
        chaining.addJobChainLink(collectionKey, enrichmentKey);
        scheduler.getListenerManager().addJobListener(chaining);

        try {
            scheduler.addJob(enrichment, false);
            scheduler.scheduleJob(collection, TriggerBuilder.newTrigger()
                    .withIdentity("collect-report-trigger", "reports")
                    .startNow()
                    .forJob(collectionKey)
                    .build());
            scheduler.start();

            assertTrue(log.await(), "Quartz did not run the chained report stages");
            assertEquals(List.of("collected", "enriched"), log.events());
        } finally {
            scheduler.shutdown(true);
        }
    }

    private static final class ExecutionLog {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);

        private JobDataMap asData() {
            JobDataMap data = new JobDataMap();
            data.put("executionLog", this);
            return data;
        }

        private void record(String event) {
            events.add(event);
        }

        private void complete() {
            completed.countDown();
        }

        private boolean await() throws InterruptedException {
            return completed.await(10, TimeUnit.SECONDS);
        }

        private List<String> events() {
            return List.copyOf(events);
        }
    }

    public static final class CollectionJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            ExecutionLog log = (ExecutionLog) context.getMergedJobDataMap().get("executionLog");
            log.record("collected");
        }
    }

    public static final class EnrichmentJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            ExecutionLog log = (ExecutionLog) context.getMergedJobDataMap().get("executionLog");
            log.record("enriched");
            log.complete();
        }
    }
}
