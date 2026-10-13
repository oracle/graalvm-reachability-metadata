/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_quartz;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;

import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzJobDetailsDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzJobGroupSummaryDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzJobTriggerDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzTriggerGroupSummaryDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_quartzTest {

    private static final JobKey JOB_KEY = JobKey.jobKey("reportableJob", "reportableGroup");

    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("reportableTrigger", "reportableGroup");

    @Test
    void quartzEndpointReportsAndTriggersScheduledJobs() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        try {
            scheduler.start();
            JobDetail job = JobBuilder.newJob(EndpointJob.class)
                    .withIdentity(JOB_KEY)
                    .withDescription("A job exposed through the Quartz endpoint")
                    .usingJobData("payload", "scheduled-payload")
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_KEY)
                    .forJob(JOB_KEY)
                    .startAt(new Date(System.currentTimeMillis() + 60_000))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInHours(1).withRepeatCount(0))
                    .build();
            scheduler.scheduleJob(job, trigger);

            QuartzEndpoint endpoint = new QuartzEndpoint(scheduler, List.of());

            QuartzDescriptor report = endpoint.quartzReport();
            assertThat(report.getJobs().getGroups()).contains(JOB_KEY.getGroup());
            assertThat(report.getTriggers().getGroups()).contains(TRIGGER_KEY.getGroup());

            QuartzJobGroupSummaryDescriptor jobGroup = endpoint.quartzJobGroupSummary(JOB_KEY.getGroup());
            assertThat(jobGroup.getJobs()).containsKey(JOB_KEY.getName());
            assertThat(jobGroup.getJobs().get(JOB_KEY.getName()).getClassName()).isEqualTo(EndpointJob.class.getName());

            QuartzTriggerGroupSummaryDescriptor triggerGroup = endpoint.quartzTriggerGroupSummary(TRIGGER_KEY.getGroup());
            assertThat(triggerGroup.getTriggers().getSimple()).containsKey(TRIGGER_KEY.getName());
            Object triggerSummary = triggerGroup.getTriggers().getSimple().get(TRIGGER_KEY.getName());
            assertThat(triggerSummary).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> triggerSummaryMap = (Map<String, Object>) triggerSummary;
            assertThat(triggerSummaryMap).containsEntry("interval", 3_600_000L);

            QuartzJobDetailsDescriptor jobDetails = endpoint.quartzJob(JOB_KEY.getGroup(), JOB_KEY.getName(), true);
            assertThat(jobDetails.getDescription()).isEqualTo("A job exposed through the Quartz endpoint");
            assertThat(jobDetails.getData()).containsEntry("payload", "scheduled-payload");
            assertThat(jobDetails.getTriggers()).hasSize(1);
            assertThat(jobDetails.getTriggers().get(0)).containsEntry("name", TRIGGER_KEY.getName());

            QuartzJobTriggerDescriptor triggeredJob = endpoint.triggerQuartzJob(JOB_KEY.getGroup(), JOB_KEY.getName());
            assertThat(triggeredJob.getGroup()).isEqualTo(JOB_KEY.getGroup());
            assertThat(triggeredJob.getName()).isEqualTo(JOB_KEY.getName());
            assertThat(triggeredJob.getClassName()).isEqualTo(EndpointJob.class.getName());
            assertThat(triggeredJob.getTriggerTime()).isNotNull();
        } finally {
            scheduler.shutdown(true);
        }
    }

    public static class EndpointJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
        }

    }

}
