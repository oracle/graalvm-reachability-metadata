/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import io.quarkus.logging.LoggingFilter;
import io.quarkus.runtime.logging.LogFilterFactory;

public class LogFilterFactoryInnerReflectionLogFilterFactoryTest {
    @Test
    void createsConfiguredFilterAndAppliesItsPolicy() throws Exception {
        LogFilterFactory factory = LogFilterFactory.load();
        Filter filter = factory.create(WarningFilter.class.getName());

        assertThat(factory.priority()).isEqualTo(LogFilterFactory.MIN_PRIORITY);
        assertThat(filter.isLoggable(new LogRecord(Level.INFO, "Routine request"))).isFalse();
        assertThat(filter.isLoggable(new LogRecord(Level.WARNING, "Retry exhausted"))).isTrue();
        assertThat(filter.isLoggable(new LogRecord(Level.SEVERE, "Service unavailable"))).isTrue();
        assertThat(factory.create(WarningFilter.class.getName())).isNotSameAs(filter);
    }

    @LoggingFilter(name = "warnings")
    public static class WarningFilter implements Filter {
        @Override
        public boolean isLoggable(LogRecord record) {
            return record.getLevel().intValue() >= Level.WARNING.intValue();
        }
    }
}
