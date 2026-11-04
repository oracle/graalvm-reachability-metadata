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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.DynamicThresholdFilter;
import ch.qos.logback.classic.turbo.MDCValueLevelPair;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies tenant-specific verbosity with Logback's dynamic threshold turbo-filter subsystem.
 *
 * A catalog service keeps routine diagnostics out of its shared log stream for regular tenants,
 * while a premium tenant receives debug details without changing the service's logging calls.
 */
class DynamicThresholdLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureTenantLogging() {
        MDC.clear();
        context = new LoggerContext();
        context.setName("dynamic-threshold-test");
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.start();

        DynamicThresholdFilter thresholdFilter = new DynamicThresholdFilter();
        thresholdFilter.setContext(context);
        thresholdFilter.setKey("tenantPlan");
        thresholdFilter.setDefaultThreshold(Level.WARN);
        thresholdFilter.setOnHigherOrEqual(FilterReply.ACCEPT);
        thresholdFilter.setOnLower(FilterReply.DENY);

        MDCValueLevelPair premiumPlan = new MDCValueLevelPair();
        premiumPlan.setValue("premium");
        premiumPlan.setLevel(Level.DEBUG);
        thresholdFilter.addMDCValueLevelPair(premiumPlan);
        thresholdFilter.start();
        context.addTurboFilter(thresholdFilter);

        logger = context.getLogger(CatalogService.class);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void stopTenantLogging() {
        MDC.clear();
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void raisesDiagnosticVerbosityOnlyForPremiumTenants() {
        CatalogService service = new CatalogService(logger);

        service.refresh("regular");
        service.reportUnavailable("regular");

        MDC.put("tenantPlan", "premium");
        service.refresh("premium");

        assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("catalog unavailable for regular", "refreshed catalog for premium");
        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events.list.get(1).getLevel()).isEqualTo(Level.DEBUG);
    }

    /** Refreshes catalog data through the logging API as an application service would. */
    static final class CatalogService {

        private final Logger logger;

        CatalogService(Logger logger) {
            this.logger = logger;
        }

        void refresh(String tenant) {
            logger.debug("refreshed catalog for {}", tenant);
        }

        void reportUnavailable(String tenant) {
            logger.warn("catalog unavailable for {}", tenant);
        }
    }
}
