/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routes application events through Logback's logger hierarchy and additivity subsystem.
 *
 * An order service logs through the SLF4J facade using its class logger. Logback sends the event
 * to the service sink and, while additivity is enabled, to the orders sink as well. The service
 * can then isolate a noisy child logger without changing its application logging calls.
 */
class LoggerHierarchyRoutingTest {

    private LoggerContext context;

    private Logger ordersLogger;

    private Logger serviceLogger;

    private ListAppender<ILoggingEvent> ordersEvents;

    private ListAppender<ILoggingEvent> serviceEvents;

    @BeforeEach
    void configureLoggerHierarchy() {
        context = new LoggerContext();
        context.setName("logger-hierarchy-test");
        context.start();

        ordersLogger = context.getLogger("orders");
        ordersLogger.setLevel(Level.INFO);
        ordersLogger.setAdditive(false);
        ordersEvents = new ListAppender<>();
        ordersEvents.setContext(context);
        ordersEvents.setName("orders-sink");
        ordersEvents.start();
        ordersLogger.addAppender(ordersEvents);

        serviceLogger = context.getLogger("orders.OrderService");
        serviceLogger.setLevel(Level.DEBUG);
        serviceLogger.setAdditive(true);
        serviceEvents = new ListAppender<>();
        serviceEvents.setContext(context);
        serviceEvents.setName("service-sink");
        serviceEvents.start();
        serviceLogger.addAppender(serviceEvents);
    }

    @AfterEach
    void stopLoggerHierarchy() {
        serviceLogger.detachAndStopAllAppenders();
        ordersLogger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void propagatesEventsToParentAndCanIsolateTheChildLogger() {
        OrderService service = new OrderService(serviceLogger);

        service.accept("order-17");

        assertThat(serviceEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-17");
        assertThat(ordersEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-17");

        serviceLogger.setAdditive(false);
        service.accept("order-18");

        assertThat(serviceEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-17", "accepted order-18");
        assertThat(ordersEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-17");
    }

    /** Logs order events through the facade as an application service would. */
    static final class OrderService {

        private final org.slf4j.Logger logger;

        OrderService(org.slf4j.Logger logger) {
            this.logger = logger;
        }

        void accept(String orderId) {
            logger.info("accepted {}", orderId);
        }
    }
}
