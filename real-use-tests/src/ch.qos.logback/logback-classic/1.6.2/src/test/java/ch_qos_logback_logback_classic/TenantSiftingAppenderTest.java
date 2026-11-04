/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.util.LinkedHashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.sift.MDCBasedDiscriminator;
import ch.qos.logback.classic.sift.SiftingAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.sift.AppenderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routes tenant-specific application events with Logback's SiftingAppender subsystem.
 *
 * An order service sends events for several tenants through one logger. Logback creates a
 * dedicated event stream for each tenant from the MDC value, while events without a tenant use
 * the configured fallback stream. This lets an application keep tenant logs separate without
 * constructing or managing a logger for every tenant.
 */
class TenantSiftingAppenderTest {

    private LoggerContext context;

    private Logger logger;

    private TenantAppenderFactory appenderFactory;

    private SiftingAppender siftingAppender;

    @BeforeEach
    void configureTenantRouting() {
        MDC.clear();
        context = new LoggerContext();
        context.setName("tenant-sifting-test");
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.start();

        logger = context.getLogger(OrderService.class);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);

        MDCBasedDiscriminator discriminator = new MDCBasedDiscriminator();
        discriminator.setContext(context);
        discriminator.setKey("tenant");
        discriminator.setDefaultValue("unassigned");
        discriminator.start();

        appenderFactory = new TenantAppenderFactory();
        siftingAppender = new SiftingAppender();
        siftingAppender.setContext(context);
        siftingAppender.setDiscriminator(discriminator);
        siftingAppender.setAppenderFactory(appenderFactory);
        siftingAppender.start();
        logger.addAppender(siftingAppender);
    }

    @AfterEach
    void stopTenantRouting() {
        MDC.clear();
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void routesEventsToTheTenantStreamSelectedByTheRequestContext() {
        OrderService service = new OrderService(logger);

        MDC.put("tenant", "northwind");
        service.accept("order-7");

        MDC.put("tenant", "contoso");
        service.accept("order-8");

        MDC.remove("tenant");
        service.accept("order-9");

        ListAppender<ILoggingEvent> northwindEvents = appenderFactory.appenderFor("northwind");
        ListAppender<ILoggingEvent> contosoEvents = appenderFactory.appenderFor("contoso");
        ListAppender<ILoggingEvent> unassignedEvents = appenderFactory.appenderFor("unassigned");

        assertThat(northwindEvents).isNotNull();
        assertThat(northwindEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-7");
        assertThat(contosoEvents).isNotNull();
        assertThat(contosoEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-8");
        assertThat(unassignedEvents).isNotNull();
        assertThat(unassignedEvents.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("accepted order-9");
    }

    /** Performs order logging as an application service would. */
    static final class OrderService {

        private final Logger logger;

        OrderService(Logger logger) {
            this.logger = logger;
        }

        void accept(String orderId) {
            logger.info("accepted {}", orderId);
        }
    }

    /** Builds the in-memory sink that represents one tenant's application log stream. */
    static final class TenantAppenderFactory implements AppenderFactory<ILoggingEvent> {

        private final Map<String, ListAppender<ILoggingEvent>> appenders = new LinkedHashMap<>();

        @Override
        public Appender<ILoggingEvent> buildAppender(Context context, String discriminatingValue) {
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.setContext(context);
            appender.setName("tenant-" + discriminatingValue);
            appender.start();
            appenders.put(discriminatingValue, appender);
            return appender;
        }

        ListAppender<ILoggingEvent> appenderFor(String discriminatingValue) {
            return appenders.get(discriminatingValue);
        }
    }
}
