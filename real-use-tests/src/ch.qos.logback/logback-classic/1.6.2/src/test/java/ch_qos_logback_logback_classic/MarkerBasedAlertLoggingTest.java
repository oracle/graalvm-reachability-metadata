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
import ch.qos.logback.classic.turbo.MarkerFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Selects operational events with Logback's marker-based turbo-filter subsystem.
 *
 * A payment service sends ordinary progress messages and security alerts through the same logger.
 * Its Logback configuration keeps only marked alerts in the security stream, so an operator gets
 * the events that require attention without maintaining a separate logger for that stream.
 */
class MarkerBasedAlertLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> securityEvents;

    @BeforeEach
    void configureSecurityLogging() {
        context = new LoggerContext();
        context.setName("marker-alert-test");
        context.start();

        MarkerFilter markerFilter = new MarkerFilter();
        markerFilter.setContext(context);
        markerFilter.setMarker("SECURITY");
        markerFilter.setOnMatch("ACCEPT");
        markerFilter.setOnMismatch("DENY");
        markerFilter.start();
        context.addTurboFilter(markerFilter);

        logger = context.getLogger(PaymentService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        securityEvents = new ListAppender<>();
        securityEvents.setContext(context);
        securityEvents.start();
        logger.addAppender(securityEvents);
    }

    @AfterEach
    void stopSecurityLogging() {
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void keepsOnlyMarkedSecurityEventsInTheAlertStream() {
        PaymentService service = new PaymentService(logger);

        service.recordPayment("payment-17");
        service.reportSecurityAlert("payment-17");

        assertThat(securityEvents.list).hasSize(1);
        ILoggingEvent alert = securityEvents.list.get(0);
        assertThat(alert.getLevel()).isEqualTo(Level.WARN);
        assertThat(alert.getMarker().getName()).isEqualTo("SECURITY");
        assertThat(alert.getFormattedMessage()).isEqualTo("security review required for payment-17");
    }

    /** Performs payment logging as an application service would. */
    static final class PaymentService {

        private static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");

        private final Logger logger;

        PaymentService(Logger logger) {
            this.logger = logger;
        }

        void recordPayment(String paymentId) {
            logger.info("recorded payment {}", paymentId);
        }

        void reportSecurityAlert(String paymentId) {
            logger.warn(SECURITY, "security review required for {}", paymentId);
        }
    }
}
