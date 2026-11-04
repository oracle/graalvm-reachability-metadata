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
 * Formats application log records with Logback's pattern layout and encoder subsystem.
 *
 * A payment service configures a structured, line-oriented format for its operational log. The
 * service writes a completed payment through its normal logger, and the configured encoder emits
 * the level and rendered message in the format that a log collector receives.
 */
class PatternLayoutLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private PatternLayoutEncoder encoder;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureApplicationLogging() {
        context = new LoggerContext();
        context.setName("pattern-layout-test");
        context.start();

        encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%level|%msg%n");
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
    void stopApplicationLogging() {
        logger.detachAndStopAllAppenders();
        encoder.stop();
        context.stop();
    }

    @Test
    void writesTheConfiguredFormatToTheApplicationLog() {
        assertThat(logger.isInfoEnabled()).isTrue();
        new PaymentService(logger).complete("payment-42");
        assertThat(events.list).hasSize(1);

        String renderedLog = new String(encoder.encode(events.list.get(0)), StandardCharsets.UTF_8);

        assertThat(renderedLog).isEqualTo("INFO|completed payment-42" + System.lineSeparator());
    }

    /** Completes payments through the logger as an application service would. */
    static final class PaymentService {

        private final Logger logger;

        PaymentService(Logger logger) {
            this.logger = logger;
        }

        void complete(String paymentId) {
            logger.info("completed {}", paymentId);
        }
    }
}
