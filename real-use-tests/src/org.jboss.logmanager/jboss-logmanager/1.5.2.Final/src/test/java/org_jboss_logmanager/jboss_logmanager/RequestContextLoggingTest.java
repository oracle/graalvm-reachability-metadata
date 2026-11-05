/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.MDC;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.AsyncHandler;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises request-context logging through JBoss LogManager's MDC, formatter, and async handler APIs.
public class RequestContextLoggingTest {
    @Test
    @Timeout(60)
    public void publishesAnAsyncLogEntryWithRequestContext() throws Exception {
        RecordingOutputStream output = new RecordingOutputStream();
        OutputStreamHandler streamHandler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] request=%X{requestId} %s%n"));
        AsyncHandler asyncHandler = new AsyncHandler(16);
        asyncHandler.addHandler(streamHandler);

        Logger logger = LogContext.create().getLogger("application.checkout");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(asyncHandler);

        String previousRequestId = MDC.put("requestId", "request-42");
        try {
            logger.info("inventory reserved");

            assertTrue(output.awaitWrite(10, TimeUnit.SECONDS));
            String rendered = output.text();
            assertTrue(rendered.contains("INFO"));
            assertTrue(rendered.contains("application.checkout"));
            assertTrue(rendered.contains("request=request-42"));
            assertTrue(rendered.contains("inventory reserved"));
        } finally {
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
            logger.clearHandlers();
            asyncHandler.close();
        }
    }

    private static final class RecordingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private final CountDownLatch writeCompleted = new CountDownLatch(1);

        @Override
        public synchronized void write(int value) {
            delegate.write(value);
            writeCompleted.countDown();
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length);
            writeCompleted.countDown();
        }

        private boolean awaitWrite(long timeout, TimeUnit unit) throws InterruptedException {
            return writeCompleted.await(timeout, unit);
        }

        private synchronized String text() {
            return delegate.toString(StandardCharsets.UTF_8);
        }
    }
}
