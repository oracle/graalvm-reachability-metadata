/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(0)
public class DirectJDKLogTest {

    @Test
    void loggerUsesJdkLoggingAtConfiguredLevel() {
        String loggerName = "direct-jdk-logger";
        Logger julLogger = Logger.getLogger(loggerName);
        Level originalLevel = julLogger.getLevel();
        boolean originalUseParentHandlers = julLogger.getUseParentHandlers();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamHandler handler = new StreamHandler(output, new SimpleFormatter());
        handler.setLevel(Level.ALL);
        julLogger.addHandler(handler);

        try {
            julLogger.setUseParentHandlers(false);
            julLogger.setLevel(Level.WARNING);

            Log log = LogFactory.getLog(loggerName);

            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isDebugEnabled()).isFalse();
            log.warn("JDK logging remains operational");
            log.debug("debug logging remains disabled");
            handler.flush();

            String logged = output.toString(StandardCharsets.UTF_8);
            assertThat(logged).contains("JDK logging remains operational");
            assertThat(logged).doesNotContain("debug logging remains disabled");
        } finally {
            julLogger.setLevel(originalLevel);
            julLogger.setUseParentHandlers(originalUseParentHandlers);
            julLogger.removeHandler(handler);
            handler.close();
        }
    }
}
