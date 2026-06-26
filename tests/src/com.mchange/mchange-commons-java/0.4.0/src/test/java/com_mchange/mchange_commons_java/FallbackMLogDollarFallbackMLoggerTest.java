/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.log.FallbackMLog;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FallbackMLogDollarFallbackMLoggerTest {
    private static final String BUNDLE_NAME = "com_mchange.mchange_commons_java.FallbackMLogDollarFallbackMLoggerTestMessages";

    @Test
    void logrbResolvesMessagesFromResourceBundle() {
        MLogger logger = new FallbackMLog().getMLogger();
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;

        try (PrintStream redirectedErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setErr(redirectedErr);

            logger.logrb(
                    MLevel.INFO,
                    FallbackMLogDollarFallbackMLoggerTest.class.getName(),
                    "logrbResolvesMessagesFromResourceBundle",
                    BUNDLE_NAME,
                    "message.template",
                    new Object[]{"world"}
            );
        } finally {
            System.setErr(originalErr);
        }

        assertThat(capturedErr.toString(StandardCharsets.UTF_8))
                .contains(FallbackMLogDollarFallbackMLoggerTest.class.getName())
                .contains("logrbResolvesMessagesFromResourceBundle()")
                .contains("Resource bundle says hello to world");
    }
}
