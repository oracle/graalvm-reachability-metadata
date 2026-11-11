/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

public class LogbackServiceProviderTest {

    @Test
    void slf4jDiscoversLogbackAndDispatchesEventsWithMdc() {
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(LoggerContext.class);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger backend = context.getLogger("provider.orders");
        Level previousLevel = backend.getLevel();
        boolean previousAdditive = backend.isAdditive();
        String previousRequest = MDC.get("request");
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.setContext(context);
        events.start();
        backend.setLevel(Level.INFO);
        backend.setAdditive(false);
        backend.addAppender(events);
        try {
            MDC.put("request", "provider-request");
            LoggerFactory.getLogger("provider.orders").info("accepted {}", "order-b");

            assertThat(events.list).hasSize(1);
            ILoggingEvent event = events.list.get(0);
            assertThat(event.getFormattedMessage()).isEqualTo("accepted order-b");
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getMDCPropertyMap()).containsEntry("request", "provider-request");
        } finally {
            backend.detachAppender(events);
            backend.setLevel(previousLevel);
            backend.setAdditive(previousAdditive);
            events.stop();
            if (previousRequest == null) {
                MDC.remove("request");
            } else {
                MDC.put("request", previousRequest);
            }
        }
    }
}
