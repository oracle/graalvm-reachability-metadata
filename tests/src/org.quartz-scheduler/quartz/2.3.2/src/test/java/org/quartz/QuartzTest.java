/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.quartz;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.quartz.impl.StdSchedulerFactory;

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
}
