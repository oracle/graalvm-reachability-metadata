/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.XMLFormatter;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(0)
public class DirectJDKLogTest {

    @Test
    void defaultLoggerConfiguresAndUsesJdkLogging() {
        String loggerName = "direct-jdk-logger";
        Logger julLogger = Logger.getLogger(loggerName);
        Level originalLevel = julLogger.getLevel();
        Logger rootLogger = Logger.getLogger("");
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.OFF);
        consoleHandler.setFormatter(new XMLFormatter());
        rootLogger.addHandler(consoleHandler);

        String configClass = System.clearProperty("java.util.logging.config.class");
        String configFile = System.clearProperty("java.util.logging.config.file");
        String formatter = System.setProperty("org.apache.juli.formatter", SimpleFormatter.class.getName());

        try {
            julLogger.setLevel(Level.WARNING);

            Log log = LogFactory.getLog(loggerName);

            assertThat(consoleHandler.getFormatter()).isInstanceOf(SimpleFormatter.class);
            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isDebugEnabled()).isFalse();
            log.warn("JDK logging remains operational");
        } finally {
            julLogger.setLevel(originalLevel);
            rootLogger.removeHandler(consoleHandler);
            consoleHandler.close();
            restoreProperty("java.util.logging.config.class", configClass);
            restoreProperty("java.util.logging.config.file", configFile);
            restoreProperty("org.apache.juli.formatter", formatter);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
