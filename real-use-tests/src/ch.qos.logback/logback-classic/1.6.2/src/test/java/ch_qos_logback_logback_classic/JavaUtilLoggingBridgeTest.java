/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.util.logging.Level;
import java.util.logging.LogManager;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bridges legacy Java Util Logging records into Logback's interoperability subsystem.
 *
 * A payment gateway still uses the JDK logging API while the application's central logging
 * pipeline is Logback. The bridge delivers the gateway's warning and failure details to the
 * Logback appender, so the application can collect both APIs in one operational stream.
 */
class JavaUtilLoggingBridgeTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureLegacyLoggingBridge() {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        logger = context.getLogger("payment.gateway");
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);

        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("payment.gateway");
        julLogger.setLevel(Level.ALL);
        SLF4JBridgeHandler.install();
    }

    @AfterEach
    void stopLegacyLoggingBridge() {
        SLF4JBridgeHandler.uninstall();
        logger.detachAndStopAllAppenders();
        context.reset();
    }

    @Test
    void sendsLegacyGatewayRecordsThroughTheLogbackPipeline() {
        java.util.logging.Logger gatewayLogger = LogManager.getLogManager()
                .getLogger("payment.gateway");

        gatewayLogger.log(Level.WARNING, "gateway retry for payment-42");
        gatewayLogger.log(Level.SEVERE, "gateway failed for payment-42",
                new IllegalStateException("gateway unavailable"));

        assertThat(events.list).hasSize(2);
        assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("gateway retry for payment-42", "gateway failed for payment-42");
        assertThat(events.list.get(0).getLevel())
                .isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(events.list.get(1).getLevel())
                .isEqualTo(ch.qos.logback.classic.Level.ERROR);
        assertThat(events.list.get(1).getThrowableProxy()).isNotNull();
        assertThat(events.list.get(1).getThrowableProxy().getClassName())
                .isEqualTo(IllegalStateException.class.getName());
    }
}
