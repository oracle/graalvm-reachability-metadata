/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(0)
public class DirectJDKLogTest {

    @Test
    void defaultLoggerUsesJdkLogging() {
        String loggerName = "direct-jdk-logger";
        Logger julLogger = Logger.getLogger(loggerName);
        Level originalLevel = julLogger.getLevel();

        try {
            julLogger.setLevel(Level.WARNING);

            Log log = LogFactory.getLog(loggerName);

            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isDebugEnabled()).isFalse();
            log.warn("JDK logging remains operational");
        } finally {
            julLogger.setLevel(originalLevel);
        }
    }
}
