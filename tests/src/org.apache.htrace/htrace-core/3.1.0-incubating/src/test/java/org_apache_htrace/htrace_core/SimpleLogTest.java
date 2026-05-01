/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.htrace.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogTest {
    @Test
    void honorsExplicitLogLevelAndWritesEnabledMessages() throws Exception {
        PrintStream previousErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream capturingErr = new PrintStream(output, true, StandardCharsets.UTF_8.name())) {
            System.setErr(capturingErr);

            SimpleLog log = new SimpleLog("org.example.Component");
            log.setLevel(SimpleLog.LOG_LEVEL_WARN);

            assertThat(log.getLevel()).isEqualTo(SimpleLog.LOG_LEVEL_WARN);
            assertThat(log.isInfoEnabled()).isFalse();
            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isErrorEnabled()).isTrue();

            log.info("suppressed info message");
            log.warn("visible warning message");
        } finally {
            System.setErr(previousErr);
        }

        String stderr = output.toString(StandardCharsets.UTF_8.name());
        assertThat(stderr)
                .contains("[WARN]")
                .contains("visible warning message")
                .doesNotContain("suppressed info message");
    }
}
