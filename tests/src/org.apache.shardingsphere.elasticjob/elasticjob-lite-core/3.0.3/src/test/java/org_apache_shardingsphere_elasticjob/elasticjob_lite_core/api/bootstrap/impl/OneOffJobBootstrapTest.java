/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.api.bootstrap.impl;

import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.EmbedTestingServer;
import lombok.SneakyThrows;
import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.lite.api.bootstrap.impl.OneOffJobBootstrap;
import org.apache.shardingsphere.elasticjob.lite.internal.schedule.JobScheduleController;
import org.apache.shardingsphere.elasticjob.lite.internal.schedule.JobScheduler;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperConfiguration;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperRegistryCenter;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OneOffJobBootstrapTest {

    private static final ZookeeperConfiguration ZOOKEEPER_CONFIGURATION = new ZookeeperConfiguration(EmbedTestingServer.getConnectionString(), OneOffJobBootstrapTest.class.getSimpleName());

    private static final int SHARDING_TOTAL_COUNT = 3;

    private ZookeeperRegistryCenter zkRegCenter;

    @BeforeAll
    public static void init() {
        EmbedTestingServer.start();
    }

    @BeforeEach
    public void setUp() {
        zkRegCenter = new ZookeeperRegistryCenter(ZOOKEEPER_CONFIGURATION);
        zkRegCenter.init();
    }

    @AfterEach
    public void teardown() {
        zkRegCenter.close();
    }

    @Test
    public void assertConfigFailedWithCron() {
        assertThrows(IllegalArgumentException.class, () -> new OneOffJobBootstrap(zkRegCenter, (SimpleJob) shardingContext -> {
        }, JobConfiguration.newBuilder("test_one_off_job_execute_with_config_cron", SHARDING_TOTAL_COUNT).cron("0/5 * * * * ?").build()));
    }

    @Test
    public void assertExecute() {
        AtomicInteger counter = new AtomicInteger(0);
        final OneOffJobBootstrap oneOffJobBootstrap = new OneOffJobBootstrap(
                zkRegCenter,
                (SimpleJob) shardingContext -> counter.incrementAndGet(),
                JobConfiguration.newBuilder("test_one_off_job_execute", SHARDING_TOTAL_COUNT).build()
        );
        oneOffJobBootstrap.execute();
        blockUtilFinish(oneOffJobBootstrap, counter);
        assertThat(counter.get(), is(SHARDING_TOTAL_COUNT));
        getJobScheduler(oneOffJobBootstrap).shutdown();
    }

    @Test
    public void assertShutdown() throws SchedulerException {
        OneOffJobBootstrap oneOffJobBootstrap = new OneOffJobBootstrap(zkRegCenter, (SimpleJob) shardingContext -> {
        }, JobConfiguration.newBuilder("test_one_off_job_shutdown", SHARDING_TOTAL_COUNT).build());
        oneOffJobBootstrap.shutdown();
        assertTrue(getScheduler(oneOffJobBootstrap).isShutdown());
    }

    @SneakyThrows
    private JobScheduler getJobScheduler(final OneOffJobBootstrap oneOffJobBootstrap) {
        Field field = OneOffJobBootstrap.class.getDeclaredField("jobScheduler");
        field.setAccessible(true);
        return (JobScheduler) field.get(oneOffJobBootstrap);
    }

    @SneakyThrows
    private Scheduler getScheduler(final OneOffJobBootstrap oneOffJobBootstrap) {
        JobScheduler jobScheduler = getJobScheduler(oneOffJobBootstrap);
        Field schedulerField = JobScheduleController.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        return (Scheduler) schedulerField.get(jobScheduler.getJobScheduleController());
    }

    @SuppressWarnings("BusyWait")
    @SneakyThrows
    private void blockUtilFinish(final OneOffJobBootstrap oneOffJobBootstrap, final AtomicInteger counter) {
        Scheduler scheduler = getScheduler(oneOffJobBootstrap);
        while (0 == counter.get() || !scheduler.getCurrentlyExecutingJobs().isEmpty()) {
            Thread.sleep(100);
        }
    }
}
