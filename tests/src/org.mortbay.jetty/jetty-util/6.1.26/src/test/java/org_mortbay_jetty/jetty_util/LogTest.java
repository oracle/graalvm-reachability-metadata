/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;
import org.mortbay.log.Slf4jLog;
import org.mortbay.log.StdErrLog;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogTest {
    @Test
    public void initializesDefaultBackendAndRoutesFacadeAndNamedLoggerMessages() {
        PrintStream previousError = System.err;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            assertInstanceOf(Slf4jLog.class, Log.getLog());
            Log.info("accepted {} requests for {}", 3, "consumer");
            Logger named = Log.getLogger("consumer.requests");
            named.info("completed {} requests for {}", 2, "reader");
            String output = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("accepted 3 requests for consumer"), output);
            assertTrue(output.contains("consumer.requests"), output);
            assertTrue(output.contains("completed 2 requests for reader"), output);
        } finally {
            System.setErr(previousError);
        }
    }

    @Test
    public void formatsMessagesAndLogsNestedExceptionTargets() {
        Logger previous = Log.getLog();
        PrintStream previousError = System.err;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            StdErrLog logger = new StdErrLog("consumer");
            Log.setLog(logger);
            assertSame(logger, Log.getLogger(null));
            Log.info("processed {} entries for {}", 4, "client");
            Log.warn(new InvocationTargetException(new IllegalStateException("nested payload"), "outer wrapper"));
            String output = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("processed 4 entries for client"), output);
            assertTrue(output.contains("Nested in"), output);
            assertTrue(output.contains("nested payload"), output);
        } finally {
            Log.setLog(previous);
            System.setErr(previousError);
        }
    }
}
