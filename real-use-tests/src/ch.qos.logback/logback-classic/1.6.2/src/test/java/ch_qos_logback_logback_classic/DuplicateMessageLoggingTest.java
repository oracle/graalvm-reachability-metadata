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
import ch.qos.logback.classic.turbo.DuplicateMessageFilter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suppresses repeated application messages with Logback's duplicate-message turbo-filter subsystem.
 *
 * A worker service reports a repeated upstream failure through its normal logger. Its configured
 * filter keeps the first two reports for useful diagnosis and suppresses later copies, preventing
 * one incident from overwhelming the operational log stream.
 */
class DuplicateMessageLoggingTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void configureDuplicateMessageFiltering() {
        context = new LoggerContext();
        context.setName("duplicate-message-test");
        context.start();

        DuplicateMessageFilter duplicateFilter = new DuplicateMessageFilter();
        duplicateFilter.setContext(context);
        duplicateFilter.setAllowedRepetitions(1);
        duplicateFilter.setCacheSize(16);
        duplicateFilter.start();
        context.addTurboFilter(duplicateFilter);

        logger = context.getLogger(WorkerService.class);
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.setContext(context);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void stopDuplicateMessageFiltering() {
        logger.detachAndStopAllAppenders();
        context.stop();
    }

    @Test
    void keepsTheInitialFailureReportsAndSuppressesLaterDuplicates() {
        WorkerService service = new WorkerService(logger);

        service.reportUpstreamFailure();
        service.reportUpstreamFailure();
        service.reportUpstreamFailure();

        assertThat(events.list).hasSize(2);
        assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("upstream unavailable", "upstream unavailable");
        assertThat(events.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    /** Reports worker failures through the logging API as an application service would. */
    static final class WorkerService {

        private final Logger logger;

        WorkerService(Logger logger) {
            this.logger = logger;
        }

        void reportUpstreamFailure() {
            logger.warn("upstream unavailable");
        }
    }
}
