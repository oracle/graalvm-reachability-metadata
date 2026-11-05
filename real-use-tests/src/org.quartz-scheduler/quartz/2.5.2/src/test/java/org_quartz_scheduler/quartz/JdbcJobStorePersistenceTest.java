/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_quartz_scheduler.quartz;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.jdbcjobstore.JobStoreTX;
import org.quartz.impl.jdbcjobstore.StdJDBCDelegate;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.utils.DBConnectionManager;
import org.quartz.utils.HikariCpPoolingConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Quartz's JDBC job store persistence subsystem for a durable report job.
 * A reporting service recovers a durable job after restarting its scheduler and runs it.
 */
public class JdbcJobStorePersistenceTest {

    private static final String DATA_SOURCE_NAME = "reportDataSource";
    private static final String DATABASE_URL = "jdbc:h2:mem:quartzReports;DB_CLOSE_DELAY=-1";
    private static final String SCHEDULER_NAME = "jdbc-report-scheduler";

    @Test
    @Timeout(60)
    public void recoversDurableJobAfterSchedulerRestart() throws Exception {
        HikariCpPoolingConnectionProvider provider = newProvider();
        DBConnectionManager.getInstance().addConnectionProvider(DATA_SOURCE_NAME, provider);
        createSchema(provider.getDataSource());

        try {
            JobKey reportKey = new JobKey("inventory-report", "reports");
            JobDetail report = JobBuilder.newJob(PersistedReportJob.class)
                    .withIdentity(reportKey)
                    .usingJobData("reportName", "inventory")
                    .storeDurably()
                    .build();

            Scheduler firstScheduler = newScheduler();
            try {
                firstScheduler.addJob(report, false);
                assertNotNull(firstScheduler.getJobDetail(reportKey));
            } finally {
                firstScheduler.shutdown(true);
            }

            provider = newProvider();
            DBConnectionManager.getInstance().addConnectionProvider(DATA_SOURCE_NAME, provider);
            Scheduler restartedScheduler = newScheduler();
            RecordingListener listener = new RecordingListener();
            restartedScheduler.getListenerManager().addJobListener(listener);
            try {
                JobDetail recovered = restartedScheduler.getJobDetail(reportKey);
                assertNotNull(recovered);
                assertEquals(PersistedReportJob.class, recovered.getJobClass());
                assertEquals("inventory", recovered.getJobDataMap().getString("reportName"));

                restartedScheduler.start();
                restartedScheduler.triggerJob(reportKey);

                assertTrue(listener.await(), "Quartz did not run the recovered durable job");
                assertEquals("inventory:completed", listener.result);
            } finally {
                restartedScheduler.shutdown(true);
            }
        } finally {
            DBConnectionManager.getInstance().shutdown(DATA_SOURCE_NAME);
        }
    }

    private static HikariCpPoolingConnectionProvider newProvider()
            throws SQLException, org.quartz.SchedulerException {
        return new HikariCpPoolingConnectionProvider(
                "org.h2.Driver", DATABASE_URL, "sa", "", 4, "SELECT 1");
    }

    private static Scheduler newScheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, SCHEDULER_NAME);
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
        properties.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", "1");
        properties.setProperty(StdSchedulerFactory.PROP_JOB_STORE_CLASS, JobStoreTX.class.getName());
        properties.setProperty("org.quartz.jobStore.dataSource", DATA_SOURCE_NAME);
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", StdJDBCDelegate.class.getName());
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        return new StdSchedulerFactory(properties).getScheduler();
    }

    private static void createSchema(HikariDataSource dataSource) throws IOException, SQLException {
        try (InputStream schema = JdbcJobStorePersistenceTest.class.getResourceAsStream(
                "/org/quartz/impl/jdbcjobstore/tables_h2.sql")) {
            if (schema == null) {
                throw new IOException("Quartz H2 schema resource is missing");
            }
            String schemaSql = new String(schema.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                for (String sql : schemaSql.lines()
                        .filter(line -> !line.trim().startsWith("--"))
                        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                        .toString()
                        .split(";")) {
                    if (!sql.trim().isEmpty()) {
                        statement.execute(sql);
                    }
                }
            }
        }
    }

    public static final class PersistedReportJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            JobDataMap data = context.getMergedJobDataMap();
            context.setResult(data.getString("reportName") + ":completed");
        }
    }

    private static final class RecordingListener implements JobListener {
        private final CountDownLatch execution = new CountDownLatch(1);
        private volatile String result;

        @Override
        public String getName() {
            return "jdbc-report-recording-listener";
        }

        @Override
        public void jobToBeExecuted(JobExecutionContext context) {
        }

        @Override
        public void jobExecutionVetoed(JobExecutionContext context) {
        }

        @Override
        public void jobWasExecuted(JobExecutionContext context, JobExecutionException exception) {
            if (exception == null) {
                result = (String) context.getResult();
            }
            execution.countDown();
        }

        private boolean await() throws InterruptedException {
            return execution.await(10, TimeUnit.SECONDS);
        }
    }
}
