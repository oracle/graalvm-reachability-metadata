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
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serializes structured application events with Logback's JSON encoder subsystem.
 *
 * An order service emits a completed order with a structured identifier and a parameterized
 * message. Its log collector receives JSON containing the event's level, key-value pair, original
 * template, rendered message, and argument, rather than having to parse plain text logs.
 */
class JsonStructuredLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    private JsonEncoder encoder;

    @BeforeEach
    void configureJsonLogging() {
        context = new LoggerContext();
        context.setName("json-logging-test");
        context.start();

        logger = context.getLogger(OrderService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);

        encoder = new JsonEncoder();
        encoder.setWithSequenceNumber(false);
        encoder.setWithTimestamp(false);
        encoder.setWithNanoseconds(false);
        encoder.setWithLevel(true);
        encoder.setWithThreadName(false);
        encoder.setWithLoggerName(false);
        encoder.setWithContext(false);
        encoder.setWithMarkers(false);
        encoder.setWithMDC(false);
        encoder.setWithKVPList(true);
        encoder.setWithMessage(true);
        encoder.setWithArguments(true);
        encoder.setWithThrowable(false);
        encoder.setWithFormattedMessage(true);
    }

    @AfterEach
    void stopJsonLogging() {
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void emitsStructuredOrderDetailsForTheLogCollector() {
        new OrderService(logger).complete("order-42");

        assertThat(events.list).hasSize(1);
        String json = new String(encoder.encode(events.list.get(0)), StandardCharsets.UTF_8);

        assertThat(json).startsWith("{").endsWith("}\n")
                .contains("\"level\":\"INFO\"")
                .contains("\"kvpList\": [{\"orderId\":\"order-42\"}]")
                .contains("\"message\":\"completed {}\"")
                .contains("\"formattedMessage\":\"completed order-42\"")
                .contains("\"arguments\": [\"order-42\"]");
    }

    /** Emits order records through the logging API as an application service would. */
    static final class OrderService {

        private final Logger logger;

        OrderService(Logger logger) {
            this.logger = logger;
        }

        void complete(String orderId) {
            logger.atInfo().addKeyValue("orderId", orderId).log("completed {}", orderId);
        }
    }
}
