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

public class SimpleLog_1Test {

    @Test
    void simpleLogLoadsConfigurationFromSystemResourcesWithNullContextClassLoader() {
        SimpleLog simpleLog = SimpleLogTestSupport.newSimpleLog("example.error.logger");
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        IllegalStateException failure = new IllegalStateException("boom");
        PrintStream originalError = System.err;

        simpleLog.setLevel(SimpleLog.LOG_LEVEL_ALL);

        System.setErr(new PrintStream(capturedError, true, StandardCharsets.UTF_8));
        try {
            simpleLog.error("error message", failure);
        }
        finally {
            System.setErr(originalError);
        }

        String output = capturedError.toString(StandardCharsets.UTF_8);

        assertThat(output)
                .contains("[ERROR]")
                .contains("error message")
                .contains("IllegalStateException: boom")
                .contains("example.error.logger - ");
    }
}
