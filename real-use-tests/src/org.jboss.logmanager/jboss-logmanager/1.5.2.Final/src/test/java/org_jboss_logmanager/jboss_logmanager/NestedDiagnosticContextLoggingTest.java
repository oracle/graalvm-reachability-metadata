/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.NDC;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises nested diagnostic context for correlating logs from a request and its operation.
public class NestedDiagnosticContextLoggingTest {
    @Test
    @Timeout(60)
    public void rendersNestedRequestContextInApplicationLog() throws Exception {
        RecordingOutputStream output = new RecordingOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] ndc=%x %s%n"));
        Logger logger = LogContext.create().getLogger("application.payments");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            NDC.push("request-42");
            NDC.push("charge-card");
            assertEquals("request-42.charge-card", NDC.get());

            logger.info("payment accepted");

            assertTrue(output.awaitWrite(10, TimeUnit.SECONDS));
            String rendered = output.text();
            assertTrue(rendered.contains("INFO"));
            assertTrue(rendered.contains("application.payments"));
            assertTrue(rendered.contains("ndc=request-42.charge-card"));
            assertTrue(rendered.contains("payment accepted"));
        } finally {
            NDC.clear();
            logger.clearHandlers();
            handler.close();
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
