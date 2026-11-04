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
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Selects exact alert levels with Logback's level filter subsystem.
 *
 * A checkout service sends informational progress, warnings, and failures through its normal
 * logger. Its dedicated alert sink accepts only warnings, leaving the application free to keep
 * failures in another sink while this stream remains focused on reviewable alerts.
 */
class ExactLevelAlertLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> alerts;

    @BeforeEach
    void configureAlertLogging() {
        context = new LoggerContext();
        context.setName("exact-level-alert-test");
        context.start();

        logger = context.getLogger(CheckoutService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);

        LevelFilter warningFilter = new LevelFilter();
        warningFilter.setLevel(Level.WARN);
        warningFilter.setOnMatch(FilterReply.ACCEPT);
        warningFilter.setOnMismatch(FilterReply.DENY);
        warningFilter.start();

        alerts = new ListAppender<>();
        alerts.setContext(context);
        alerts.addFilter(warningFilter);
        alerts.start();
        logger.addAppender(alerts);
    }

    @AfterEach
    void stopAlertLogging() {
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void keepsOnlyWarningsInTheReviewAlertStream() {
        new CheckoutService(logger).process("order-42");

        assertThat(alerts.list).hasSize(1);
        assertThat(alerts.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(alerts.list.get(0).getFormattedMessage())
                .isEqualTo("payment delayed for order-42");
    }

    /** Processes checkout events through the logger as an application service would. */
    static final class CheckoutService {

        private final Logger logger;

        CheckoutService(Logger logger) {
            this.logger = logger;
        }

        void process(String orderId) {
            logger.info("started " + orderId);
            logger.warn("payment delayed for {}", orderId);
            logger.error("payment failed for " + orderId);
        }
    }
}
