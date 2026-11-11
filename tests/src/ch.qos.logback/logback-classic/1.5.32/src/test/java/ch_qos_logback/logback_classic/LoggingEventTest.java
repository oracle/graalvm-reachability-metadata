/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventTest {

    @Test
    void capturesTheIdentityOfAnUnnamedVirtualThread() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.start();
        CapturingAppender appender = new CapturingAppender();
        appender.setContext(context);
        appender.start();
        Logger logger = context.getLogger("virtual-worker");
        logger.setAdditive(false);
        logger.addAppender(appender);
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("").factory())) {
            Future<Long> threadId = executor.submit(() -> {
                logger.info("processed {}", "job-a");
                return Thread.currentThread().threadId();
            });
            long expectedId = threadId.get(10, TimeUnit.SECONDS);

            assertThat(appender.event).isNotNull();
            assertThat(appender.event.getThreadName()).isEqualTo(LoggingEvent.VIRTUAL_THREAD_NAME_PREFIX + expectedId);
            assertThat(appender.event.getFormattedMessage()).isEqualTo("processed job-a");
        } finally {
            appender.stop();
            context.stop();
        }
    }

    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {
        private ILoggingEvent event;

        @Override
        protected void append(ILoggingEvent loggingEvent) {
            loggingEvent.prepareForDeferredProcessing();
            event = loggingEvent;
        }
    }
}
