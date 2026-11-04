/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivers application log events asynchronously with Logback's AsyncAppender subsystem.
 *
 * A checkout service sends a completed order to its normal logger and lets Logback hand the
 * event to a background appender. The service waits for its operational sink to receive the
 * event, as an application would before confirming that the log was emitted.
 */
class AsyncApplicationLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private AsyncAppender asyncAppender;

    private RecordingAppender recordingAppender;

    @BeforeEach
    void configureAsynchronousLogging() {
        MDC.clear();
        context = new LoggerContext();
        context.setName("async-logging-test");
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.start();

        recordingAppender = new RecordingAppender();
        recordingAppender.setContext(context);
        recordingAppender.setName("operational-sink");
        recordingAppender.start();

        asyncAppender = new AsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName("async-logging");
        asyncAppender.addAppender(recordingAppender);
        asyncAppender.start();

        logger = context.getLogger(CheckoutService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(asyncAppender);
    }

    @AfterEach
    void stopAsynchronousLogging() {
        MDC.clear();
        logger.detachAndStopAllAppenders();
        asyncAppender.stop();
        recordingAppender.stop();
        context.stop();
    }

    @Test
    void deliversTheCompletedOrderToTheOperationalSink() throws InterruptedException {
        assertThat(logger.isInfoEnabled()).isTrue();
        assertThat(asyncAppender.isStarted()).isTrue();
        assertThat(recordingAppender.isStarted()).isTrue();
        new CheckoutService(logger).complete("order-42");
        asyncAppender.stop();

        assertThat(recordingAppender.received.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(recordingAppender.event.getLevel()).isEqualTo(Level.INFO);
        assertThat(recordingAppender.event.getFormattedMessage()).isEqualTo("completed order-42");
    }

    /** Completes orders through the logger as an application service would. */
    static final class CheckoutService {

        private final Logger logger;

        CheckoutService(Logger logger) {
            this.logger = logger;
        }

        void complete(String orderId) {
            logger.info("completed {}", orderId);
        }
    }

    /** Records the event received by the application's operational sink. */
    static final class RecordingAppender extends AppenderBase<ILoggingEvent> {

        private final CountDownLatch received = new CountDownLatch(1);

        private ILoggingEvent event;

        @Override
        protected void append(ILoggingEvent event) {
            this.event = event;
            received.countDown();
        }
    }
}
