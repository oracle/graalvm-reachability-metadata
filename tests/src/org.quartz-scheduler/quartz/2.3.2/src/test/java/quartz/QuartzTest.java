/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package quartz;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.jdbcjobstore.JobStoreTX;
import org.quartz.simpl.SimpleThreadPool;

import static org.assertj.core.api.Assertions.assertThat;

public class QuartzTest {

    @Test
    void propertyFile() {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("org/quartz/quartz.properties");
        assertThat(stream).isNotNull();
    }

    @Test
    void buildPropertyFile() {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("org/quartz/core/quartz-build.properties");
        assertThat(stream).isNotNull();
    }

    @Test
    void schemaFile() {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("org/quartz/impl/jdbcjobstore/tables_h2.sql");
        assertThat(stream).isNotNull();
    }

    @Test
    void initializeStdFactory() throws SchedulerException {
        StdSchedulerFactory factory = new StdSchedulerFactory();
        Scheduler scheduler = factory.getScheduler();
        assertThat(scheduler.getSchedulerName()).isEqualTo("DefaultQuartzScheduler");
    }

    @Test
    void typicalUseCase() throws SchedulerException, InterruptedException {
        Properties properties = new Properties();

        properties.put(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.put(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", Integer.toString(2));

        StdSchedulerFactory factory = new StdSchedulerFactory(properties);
        Scheduler scheduler = factory.getScheduler();
        scheduler.start();

        JobDetail jobDetail = JobBuilder.newJob(SimpleJob.class).withIdentity("simpleJob").build();

        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(10).repeatForever();
        Trigger trigger = TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity("simpleJobTrigger")
                .withSchedule(scheduleBuilder).build();

        // Tell quartz to schedule the job using our trigger
        scheduler.scheduleJob(jobDetail, trigger);

        Thread.sleep(100);

        scheduler.shutdown();
    }

    @Test
    void jobStoreTX() throws SchedulerException {
        Properties properties = new Properties();
        properties.put(StdSchedulerFactory.PROP_JOB_STORE_CLASS, JobStoreTX.class.getName());
        properties.put("org.quartz.jobStore.dataSource", "ds");
        properties.put(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", Integer.toString(2));
        StdSchedulerFactory factory = new StdSchedulerFactory(properties);
        Scheduler scheduler = factory.getScheduler();

        scheduler.shutdown();
    }

}
