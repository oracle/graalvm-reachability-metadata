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
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filters application events by severity with Logback's threshold filter subsystem.
 *
 * A checkout service keeps routine progress messages out of its alert stream while retaining
 * warnings that need operator attention. The service logs through its normal logger, and the
 * configured appender exposes exactly the events that meet the operational threshold.
 */
class SeverityThresholdLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> alertEvents;

    @BeforeEach
    void configureAlertLogging() {
        context = new LoggerContext();
        context.setName("severity-threshold-test");
        context.start();

        logger = context.getLogger(CheckoutService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);

        ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel("WARN");
        thresholdFilter.start();

        alertEvents = new ListAppender<>();
        alertEvents.setContext(context);
        alertEvents.addFilter(thresholdFilter);
        alertEvents.start();
        logger.addAppender(alertEvents);
    }

    @AfterEach
    void stopAlertLogging() {
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void keepsRoutineProgressOutOfTheAlertStream() {
        CheckoutService service = new CheckoutService(logger);

        service.startCheckout("order-17");
        service.reportPaymentRetry("order-17");

        assertThat(alertEvents.list).hasSize(1);
        ILoggingEvent alert = alertEvents.list.get(0);
        assertThat(alert.getLevel()).isEqualTo(Level.WARN);
        assertThat(alert.getFormattedMessage()).isEqualTo("payment retry for order-17");
    }

    /** Performs checkout logging as an application service would. */
    static final class CheckoutService {

        private final Logger logger;

        CheckoutService(Logger logger) {
            this.logger = logger;
        }

        void startCheckout(String orderId) {
            logger.info("started checkout for {}", orderId);
        }

        void reportPaymentRetry(String orderId) {
            logger.warn("payment retry for {}", orderId);
        }
    }
}
