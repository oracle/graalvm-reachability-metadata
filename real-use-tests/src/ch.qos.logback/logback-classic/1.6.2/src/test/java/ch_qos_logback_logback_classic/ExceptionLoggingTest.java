/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders application exceptions through Logback's throwable logging subsystem.
 *
 * A payment service reports a failed gateway call through its normal logger. Its configured
 * encoder emits the error message and a compact exception description for the operational log,
 * giving an operator the failure without requiring the service to format a stack trace itself.
 */
class ExceptionLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private PatternLayoutEncoder encoder;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureExceptionLogging() {
        context = new LoggerContext();
        context.setName("exception-logging-test");
        context.start();

        encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%level|%msg%n%ex{short}");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        logger = context.getLogger(PaymentService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void stopExceptionLogging() {
        logger.detachAndStopAllAppenders();
        encoder.stop();
        context.stop();
    }

    @Test
    void rendersTheGatewayFailureForTheOperationalLog() {
        new PaymentService(logger).charge("payment-42");

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();

        String renderedLog = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(renderedLog).startsWith("ERROR|payment failed for payment-42")
                .contains("java.lang.IllegalStateException: payment gateway unavailable");
    }

    /** Reports payment failures through the logging API as an application service would. */
    static final class PaymentService {

        private final Logger logger;

        PaymentService(Logger logger) {
            this.logger = logger;
        }

        void charge(String paymentId) {
            logger.error("payment failed for {}", paymentId,
                    new IllegalStateException("payment gateway unavailable"));
        }
    }
}
