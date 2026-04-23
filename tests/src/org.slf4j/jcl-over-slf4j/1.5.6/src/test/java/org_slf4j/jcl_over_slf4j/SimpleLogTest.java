/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;
import org_slf4j.jcl_over_slf4j.support.SimpleLogTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogTest {

    @Test
    void simpleLogSupportsTraceThroughFatalLevels() {
        SimpleLog simpleLog = SimpleLogTestSupport.newSimpleLog("example.trace.logger");
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        simpleLog.setLevel(SimpleLog.LOG_LEVEL_TRACE);

        System.setErr(new PrintStream(capturedError, true, StandardCharsets.UTF_8));
        try {
            assertThat(simpleLog.isTraceEnabled()).isTrue();
            assertThat(simpleLog.isDebugEnabled()).isTrue();
            assertThat(simpleLog.isInfoEnabled()).isTrue();
            assertThat(simpleLog.isWarnEnabled()).isTrue();
            assertThat(simpleLog.isErrorEnabled()).isTrue();
            assertThat(simpleLog.isFatalEnabled()).isTrue();

            simpleLog.trace("trace message");
            simpleLog.info("info message");
            simpleLog.warn("warn message");
        } finally {
            System.setErr(originalError);
        }

        String output = capturedError.toString(StandardCharsets.UTF_8);

        assertThat(output)
                .contains("[TRACE]")
                .contains("[INFO]")
                .contains("[WARN]")
                .contains("trace message")
                .contains("info message")
                .contains("warn message")
                .contains("example.trace.logger - ");
    }
}
