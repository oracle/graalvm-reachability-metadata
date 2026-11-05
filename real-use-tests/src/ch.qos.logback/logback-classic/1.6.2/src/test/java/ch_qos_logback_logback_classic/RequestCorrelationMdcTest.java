/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correlates application log events with request fields through Logback's mapped diagnostic context.
 *
 * A billing service adds a request ID and tenant to the MDC while handling an invoice. Its Logback
 * logger captures those fields on each event, including after the tenant field is removed when the
 * request moves to a shared completion step. The service can therefore give an operator both the
 * rendered messages and the exact correlation context that was active for each event.
 */
class RequestCorrelationMdcTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureRequestLogger() {
        MDC.clear();
        context = new LoggerContext();
        context.setName("request-correlation-test");
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.start();

        logger = context.getLogger(InvoiceHandler.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void stopRequestLogger() {
        MDC.clear();
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void capturesTheRequestContextOnEachEventAsTheRequestProgresses() {
        InvoiceHandler handler = new InvoiceHandler(logger);

        MDC.put("requestId", "req-2048");
        MDC.put("tenant", "northwind");
        handler.accept("invoice-7");
        Map<String, String> acceptedContext = events.list.get(0).getMDCPropertyMap();

        MDC.remove("tenant");
        handler.complete("invoice-7");

        assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted invoice-7", "completed invoice-7");

        assertThat(acceptedContext).containsEntry("requestId", "req-2048")
                .containsEntry("tenant", "northwind");

        Map<String, String> completedContext = events.list.get(1).getMDCPropertyMap();
        assertThat(completedContext).containsEntry("requestId", "req-2048")
                .doesNotContainKey("tenant");
    }

    /** Handles invoices in the same way as an application service would. */
    static final class InvoiceHandler {

        private final Logger logger;

        InvoiceHandler(Logger logger) {
            this.logger = logger;
        }

        void accept(String invoiceId) {
            logger.info("accepted {}", invoiceId);
        }

        void complete(String invoiceId) {
            logger.info("completed {}", invoiceId);
        }
    }
}
