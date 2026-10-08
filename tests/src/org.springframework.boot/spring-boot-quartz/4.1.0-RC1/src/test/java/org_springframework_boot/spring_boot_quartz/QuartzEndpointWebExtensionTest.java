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
import java.util.Set;

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

import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzGroupsDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzJobDetailsDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzJobGroupSummaryDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzJobTriggerDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpoint.QuartzTriggerGroupSummaryDescriptor;
import org.springframework.boot.quartz.actuate.endpoint.QuartzEndpointWebExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class QuartzEndpointWebExtensionTest {

    private static final JobKey JOB_KEY = JobKey.jobKey("webJob", "webGroup");

    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("webTrigger", "webGroup");

    @Test
    void quartzEndpointWebExtensionExposesQuartzOperations() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");

        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        try {
            scheduler.start();
            JobDetail job = JobBuilder.newJob(WebEndpointJob.class)
                    .withIdentity(JOB_KEY)
                    .withDescription("A job exposed through the Quartz web endpoint")
                    .usingJobData("payload", "web-payload")
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
            QuartzEndpointWebExtension extension = new QuartzEndpointWebExtension(endpoint, Show.ALWAYS, Set.of());

            WebEndpointResponse<QuartzGroupsDescriptor> jobGroups =
                    extension.quartzJobOrTriggerGroups("jobs");
            assertThat(jobGroups.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(jobGroups.getBody().getGroups()).containsKey(JOB_KEY.getGroup());

            WebEndpointResponse<QuartzGroupsDescriptor> triggerGroups =
                    extension.quartzJobOrTriggerGroups("triggers");
            assertThat(triggerGroups.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(triggerGroups.getBody().getGroups()).containsKey(TRIGGER_KEY.getGroup());

            WebEndpointResponse<Object> jobGroup = extension.quartzJobOrTriggerGroup("jobs", JOB_KEY.getGroup());
            assertThat(jobGroup.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(jobGroup.getBody()).isInstanceOf(QuartzJobGroupSummaryDescriptor.class);

            WebEndpointResponse<Object> triggerGroup = extension.quartzJobOrTriggerGroup("triggers",
                    TRIGGER_KEY.getGroup());
            assertThat(triggerGroup.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(triggerGroup.getBody()).isInstanceOf(QuartzTriggerGroupSummaryDescriptor.class);

            WebEndpointResponse<Object> jobDetailsResponse = extension.quartzJobOrTrigger(SecurityContext.NONE,
                    "jobs", JOB_KEY.getGroup(), JOB_KEY.getName());
            assertThat(jobDetailsResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(jobDetailsResponse.getBody()).isInstanceOf(QuartzJobDetailsDescriptor.class);
            QuartzJobDetailsDescriptor jobDetails = (QuartzJobDetailsDescriptor) jobDetailsResponse.getBody();
            assertThat(jobDetails.getData()).containsEntry("payload", "web-payload");

            WebEndpointResponse<Object> triggerDetailsResponse = extension.quartzJobOrTrigger(SecurityContext.NONE,
                    "triggers", TRIGGER_KEY.getGroup(), TRIGGER_KEY.getName());
            assertThat(triggerDetailsResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(triggerDetailsResponse.getBody()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> triggerDetails = (Map<String, Object>) triggerDetailsResponse.getBody();
            assertThat(triggerDetails).containsEntry("group", TRIGGER_KEY.getGroup())
                    .containsEntry("name", TRIGGER_KEY.getName());

            WebEndpointResponse<Object> triggerResponse = extension.triggerQuartzJob("jobs", JOB_KEY.getGroup(),
                    JOB_KEY.getName(), "running");
            assertThat(triggerResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(triggerResponse.getBody()).isInstanceOf(QuartzJobTriggerDescriptor.class);
        }
        finally {
            scheduler.shutdown(true);
        }
    }

    public static class WebEndpointJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
        }

    }

}
