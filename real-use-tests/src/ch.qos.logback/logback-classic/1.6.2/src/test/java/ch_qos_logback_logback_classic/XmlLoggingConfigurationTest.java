/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads application logging policies through Logback's XML configuration subsystem.
 *
 * An order service uses a logging policy supplied by its deployment rather than setting logger
 * levels in application code. The configured policy keeps informational order events while
 * omitting debug details from the service's operational stream.
 */
class XmlLoggingConfigurationTest {

    private static final String CONFIGURATION = """
            <configuration>
              <logger name="application.orders" level="INFO" additivity="false"/>
            </configuration>
            """;

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureApplicationLogging() throws Exception {
        context = new LoggerContext();
        context.setName("xml-configuration-test");
        context.start();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(
                CONFIGURATION.getBytes(StandardCharsets.UTF_8)));

        logger = context.getLogger("application.orders");
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void stopApplicationLogging() {
        if (logger != null) {
            logger.detachAndStopAllAppenders();
        }
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void appliesDeploymentLoggingPolicyToOrderEvents() {
        assertThat(logger.isInfoEnabled()).isTrue();
        assertThat(logger.isDebugEnabled()).isFalse();

        new OrderService(logger).complete("order-42");
        logger.debug("internal details for order-42");

        assertThat(events.list).hasSize(1);
        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(events.list.get(0).getFormattedMessage()).isEqualTo("completed order-42");
    }

    /** Completes orders through the configured logging API as an application service would. */
    static final class OrderService {

        private final Logger logger;

        OrderService(Logger logger) {
            this.logger = logger;
        }

        void complete(String orderId) {
            logger.info("completed {}", orderId);
        }
    }
}
