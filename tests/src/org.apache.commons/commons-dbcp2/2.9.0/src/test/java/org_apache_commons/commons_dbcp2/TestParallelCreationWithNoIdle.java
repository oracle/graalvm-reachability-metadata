/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParallelCreationWithNoIdle {
    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    class TestThread extends Thread {
        final java.util.Random _random = new java.util.Random();
        final int iter;
        final int delay;
        final int delayAfter;

        TestThread(final int iter, final int delay, final int delayAfter) {
            this.iter = iter;
            this.delay = delay;
            this.delayAfter = delayAfter;
        }

        @Override
        public void run() {
            IntStream.range(0, iter).forEach(i -> {
                sleepMax(delay);
                try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement("select 'literal', SYSDATE from dual")) {
                    final ResultSet rset = stmt.executeQuery();
                    rset.next();
                    sleepMax(delayAfter);
                    rset.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
        }

        private void sleepMax(final int timeMax) {
            if (timeMax == 0) {
                return;
            }
            try {
                Thread.sleep(_random.nextInt(timeMax));
            } catch (final Exception e) {
            }
        }
    }

    private static final String CATALOG = "test catalog";

    @BeforeAll
    public static void setUpClass() {
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org_apache_commons.commons_dbcp2.StackMessageLog");
    }

    protected BasicDataSource ds;

    @BeforeEach
    public void setUp() throws Exception {
        ds = new BasicDataSource();
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterConnectionDelayDriver");
        ds.setUrl("jdbc:apache:commons:testerConnectionDelayDriver:50");
        ds.setMaxTotal(10);
        ds.setMaxIdle(0);
        ds.setMaxWaitMillis(60000);
        ds.setDefaultAutoCommit(Boolean.TRUE);
        ds.setDefaultReadOnly(Boolean.FALSE);
        ds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        ds.setDefaultCatalog(CATALOG);
        ds.setUsername("userName");
        ds.setPassword("password");
        ds.setValidationQuery("SELECT DUMMY FROM DUAL");
        ds.setConnectionInitSqls(Arrays.asList("SELECT 1", "SELECT 2"));
        ds.setDriverClassLoader(new TesterClassLoader());
        ds.setJmxName("org.apache.commons.dbcp2:name=test");
    }

    @Test
    public void testMassiveConcurrentInitBorrow() throws Exception {
        final int numThreads = 200;
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterConnectionDelayDriver");
        ds.setUrl("jdbc:apache:commons:testerConnectionDelayDriver:20");
        ds.setInitialSize(8);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        final Thread[] threads = new Thread[numThreads];
        IntStream.range(0, numThreads).forEach(i -> {
            threads[i] = new TestThread(2, 0, 50);
            threads[i].setUncaughtExceptionHandler((t, e) -> errors.add(e));
        });
        for (int i = 0; i < numThreads; i++) {
            threads[i].start();
            if (i % 4 == 0) {
                Thread.sleep(20);
            }
        }
        for (int i = 0; i < numThreads; i++) {
            threads[i].join();
        }
        assertEquals(0, errors.size());
        ds.close();
    }

}
