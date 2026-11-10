/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogFactoryTest {
    @Test
    void selectsConsoleAndSilentLoggingImplementations() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previousOutput = System.out;
        Log previousLog = LogFactory.getLog(getClass());
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            LogFactory.useStdOutLogging();
            Log console = LogFactory.getLog(getClass());
            assertTrue(console.isDebugEnabled());
            console.debug("console SQL");
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("console SQL"));

            LogFactory.useNoLogging();
            output.reset();
            Log silent = LogFactory.getLog(getClass());
            assertFalse(silent.isDebugEnabled());
            assertFalse(silent.isTraceEnabled());
            silent.debug("silent SQL");
            silent.warn("silent warning");
            silent.error("silent error");
            assertEquals("", output.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(previousOutput);
            LogFactory.useCustomLogging(previousLog.getClass());
        }
    }
}
