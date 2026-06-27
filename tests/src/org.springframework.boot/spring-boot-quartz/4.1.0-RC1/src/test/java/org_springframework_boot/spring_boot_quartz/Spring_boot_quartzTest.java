/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_quartz;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.simpl.RAMJobStore;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.quartz.autoconfigure.JobStoreType;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_quartzTest {

    private static final JobKey JOB_KEY = JobKey.jobKey("sampleJob", "sampleGroup");

    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("sampleTrigger", "sampleGroup");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QuartzAutoConfiguration.class));

    @Test
    void quartzPropertiesExposeDefaultsAndSetters() {
        QuartzProperties properties = new QuartzProperties();

        assertThat(properties.getJobStoreType()).isEqualTo(JobStoreType.MEMORY);
        assertThat(properties.getSchedulerName()).isNull();
        assertThat(properties.isAutoStartup()).isTrue();
        assertThat(properties.getStartupDelay()).isEqualTo(Duration.ZERO);
        assertThat(properties.isWaitForJobsToCompleteOnShutdown()).isFalse();
        assertThat(properties.isOverwriteExistingJobs()).isFalse();
        assertThat(properties.getProperties()).isEmpty();

        properties.setJobStoreType(JobStoreType.JDBC);
        properties.setSchedulerName("ordersScheduler");
        properties.setAutoStartup(false);
        properties.setStartupDelay(Duration.ofSeconds(5));
        properties.setWaitForJobsToCompleteOnShutdown(true);
        properties.setOverwriteExistingJobs(true);
        properties.getProperties().put("org.quartz.threadPool.threadCount", "2");

        assertThat(properties.getJobStoreType()).isEqualTo(JobStoreType.JDBC);
        assertThat(properties.getSchedulerName()).isEqualTo("ordersScheduler");
        assertThat(properties.isAutoStartup()).isFalse();
        assertThat(properties.getStartupDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.isWaitForJobsToCompleteOnShutdown()).isTrue();
        assertThat(properties.isOverwriteExistingJobs()).isTrue();
        assertThat(properties.getProperties()).containsEntry("org.quartz.threadPool.threadCount", "2");
    }

    @Test
    void autoConfigurationCreatesInMemorySchedulerAndBindsSettings() {
        this.contextRunner
                .withPropertyValues("spring.quartz.auto-startup=false", "spring.quartz.scheduler-name=ordersScheduler",
                        "spring.quartz.startup-delay=3s",
                        "spring.quartz.wait-for-jobs-to-complete-on-shutdown=true",
                        "spring.quartz.overwrite-existing-jobs=true",
                        "spring.quartz.properties[org.quartz.threadPool.threadCount]=2")
                .run((context) -> {
                    assertThat(context).hasSingleBean(QuartzProperties.class);
                    assertThat(context).hasSingleBean(Scheduler.class);
                    assertThat(context).hasBean("quartzScheduler");

                    QuartzProperties properties = context.getBean(QuartzProperties.class);
                    assertThat(properties.getJobStoreType()).isEqualTo(JobStoreType.MEMORY);
                    assertThat(properties.getSchedulerName()).isEqualTo("ordersScheduler");
                    assertThat(properties.isAutoStartup()).isFalse();
                    assertThat(properties.getStartupDelay()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.isWaitForJobsToCompleteOnShutdown()).isTrue();
                    assertThat(properties.isOverwriteExistingJobs()).isTrue();
                    assertThat(properties.getProperties()).containsEntry("org.quartz.threadPool.threadCount", "2");

                    Scheduler scheduler = context.getBean(Scheduler.class);
                    assertThat(scheduler.getSchedulerName()).isEqualTo("ordersScheduler");
                    assertThat(scheduler.isStarted()).isFalse();
                    assertThat(scheduler.getMetaData().getJobStoreClass()).isEqualTo(RAMJobStore.class);

                    SchedulerFactoryBean schedulerFactoryBean = context.getBean(
                            BeanFactory.FACTORY_BEAN_PREFIX + "quartzScheduler", SchedulerFactoryBean.class);
                    assertThat(schedulerFactoryBean).isNotNull();
                });
    }

    @Test
    void autoConfigurationRegistersJobDetailsAndTriggersWithScheduler() {
        this.contextRunner.withPropertyValues("spring.quartz.auto-startup=false")
                .withBean("sampleJobDetail", JobDetail.class, Spring_boot_quartzTest::sampleJobDetail)
                .withBean("sampleTrigger", Trigger.class, Spring_boot_quartzTest::sampleTrigger)
                .run((context) -> {
                    Scheduler scheduler = context.getBean(Scheduler.class);

                    assertThat(scheduler.checkExists(JOB_KEY)).isTrue();
                    assertThat(scheduler.checkExists(TRIGGER_KEY)).isTrue();
                    assertThat(scheduler.getTriggersOfJob(JOB_KEY)).extracting(Trigger::getKey)
                            .containsExactly(TRIGGER_KEY);

                    JobDetail jobDetail = scheduler.getJobDetail(JOB_KEY);
                    assertThat(jobDetail.getJobClass()).isEqualTo(SampleQuartzJob.class);
                    assertThat(jobDetail.isDurable()).isTrue();
                    assertThat(jobDetail.getJobDataMap()).containsEntry("source", "spring-boot-quartz");

                    Trigger trigger = scheduler.getTrigger(TRIGGER_KEY);
                    assertThat(trigger.getJobKey()).isEqualTo(JOB_KEY);
                    assertThat(trigger.getJobDataMap()).containsEntry("trigger", "boot-auto-configuration");
                });
    }

    @Test
    void schedulerFactoryBeanCustomizerCanModifyCreatedScheduler() {
        AtomicBoolean customizerInvoked = new AtomicBoolean();

        this.contextRunner.withPropertyValues("spring.quartz.auto-startup=false")
                .withBean(SchedulerFactoryBeanCustomizer.class, () -> (schedulerFactoryBean) -> {
                    customizerInvoked.set(true);
                    schedulerFactoryBean.setSchedulerName("customizedScheduler");
                })
                .run((context) -> {
                    Scheduler scheduler = context.getBean(Scheduler.class);

                    assertThat(customizerInvoked).isTrue();
                    assertThat(scheduler.getSchedulerName()).isEqualTo("customizedScheduler");
                    assertThat(scheduler.isStarted()).isFalse();
                });
    }

    private static JobDetail sampleJobDetail() {
        JobDataMap data = new JobDataMap();
        data.put("source", "spring-boot-quartz");
        return JobBuilder.newJob(SampleQuartzJob.class).withIdentity(JOB_KEY).usingJobData(data).storeDurably().build();
    }

    private static Trigger sampleTrigger() {
        JobDataMap data = new JobDataMap();
        data.put("trigger", "boot-auto-configuration");
        return TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .forJob(JOB_KEY)
                .usingJobData(data)
                .startAt(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInHours(1).withRepeatCount(0))
                .build();
    }

    public static class SampleQuartzJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            assertThat(context.getJobDetail().getKey()).isEqualTo(JOB_KEY);
        }

    }

}
