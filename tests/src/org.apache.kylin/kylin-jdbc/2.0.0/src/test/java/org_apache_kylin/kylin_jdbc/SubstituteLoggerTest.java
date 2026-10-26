/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.Test;
import org.slf4j.event.LoggingEvent;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.NOPLogger;
import org.slf4j.helpers.SubstituteLogger;

import static org.assertj.core.api.Assertions.assertThat;

public class SubstituteLoggerTest {
    @Test
    void forwardsRecordedEventsToAnEventAwareDelegate() {
        SubstituteLogger logger = new SubstituteLogger("query", new LinkedBlockingQueue<>(), false);
        EventAwareLogger delegate = new EventAwareLogger();
        SubstituteLoggingEvent event = new SubstituteLoggingEvent();
        event.setMessage("completed");
        logger.setDelegate(delegate);

        assertThat(logger.isDelegateEventAware()).isTrue();
        logger.log(event);

        assertThat(delegate.event).isSameAs(event);
        assertThat(delegate.event.getMessage()).isEqualTo("completed");
    }

    public static class EventAwareLogger extends NOPLogger {
        private LoggingEvent event;

        public void log(LoggingEvent event) {
            this.event = event;
        }
    }
}
