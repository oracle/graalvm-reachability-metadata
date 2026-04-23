/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;
import org.slf4j.spi.LocationAwareLogger;
import org_slf4j.jcl_over_slf4j.support.TestLocationAwareLogger;
import org_slf4j.jcl_over_slf4j.support.TestLocationAwareLogger.RecordedEvent;

import static org.assertj.core.api.Assertions.assertThat;

public class SLF4JLocationAwareLogTest {

    @Test
    void commonsLoggingWrapsLocationAwareSlf4jLoggers() {
        Log log = LogFactory.getLog("location-aware-logger");
        TestLocationAwareLogger logger = StaticLoggerBinder.SINGLETON
                .getTestLoggerFactory()
                .getRecordedLogger("location-aware-logger");
        IllegalArgumentException failure = new IllegalArgumentException("boom");

        assertThat(log.getClass().getName())
                .isEqualTo("org.apache.commons.logging.impl.SLF4JLocationAwareLog");
        assertThat(log.isTraceEnabled()).isTrue();
        assertThat(log.isErrorEnabled()).isTrue();

        log.trace("trace message");
        log.error("error message", failure);

        List<RecordedEvent> events = logger.getEvents();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getLevel()).isEqualTo(LocationAwareLogger.TRACE_INT);
        assertThat(events.get(0).getFqcn())
                .isEqualTo("org.apache.commons.logging.impl.SLF4JLocationAwareLog");
        assertThat(events.get(0).getMessage()).isEqualTo("trace message");
        assertThat(events.get(0).getThrowable()).isNull();
        assertThat(events.get(1).getLevel()).isEqualTo(LocationAwareLogger.ERROR_INT);
        assertThat(events.get(1).getFqcn())
                .isEqualTo("org.apache.commons.logging.impl.SLF4JLocationAwareLog");
        assertThat(events.get(1).getMessage()).isEqualTo("error message");
        assertThat(events.get(1).getThrowable()).isSameAs(failure);
    }
}
