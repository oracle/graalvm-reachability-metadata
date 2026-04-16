/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FallbackMLogFallbackMLoggerTest {
    private static final String BUNDLE_NAME = "com.mchange.v2.log.FallbackMLogFallbackMLoggerTestMessages";
    private static final String MESSAGE_KEY = "translated.message";
    private static final String TRANSLATED_MESSAGE = "Translated fallback logger message";

    @Test
    void logrbLoadsMessagesFromResourceBundles() throws Exception {
        FallbackMLog fallbackMLog = new FallbackMLog();
        MLogger logger = fallbackMLog.getMLogger();
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;

        try (PrintStream replacementErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setErr(replacementErr);
            logger.logrb(
                MLevel.INFO,
                "FallbackMLogFallbackMLoggerTest",
                "logrbLoadsMessagesFromResourceBundles",
                BUNDLE_NAME,
                MESSAGE_KEY
            );
        } finally {
            System.setErr(originalErr);
        }

        assertThat(capturedErr.toString(StandardCharsets.UTF_8)).contains(TRANSLATED_MESSAGE);
    }
}
