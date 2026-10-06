/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(-3)
public class DirectJDKLogTest {

    @Test
    void defaultLoggerInstallsAndUsesJdkFormatter() {
        Logger rootLogger = Logger.getLogger("");
        Logger directLogger = Logger.getLogger("direct-jdk-logger");
        Level previousLevel = directLogger.getLevel();
        directLogger.setLevel(Level.INFO);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new MarkerFormatter());
        rootLogger.addHandler(consoleHandler);

        String configClass = System.clearProperty("java.util.logging.config.class");
        String configFile = System.clearProperty("java.util.logging.config.file");
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(new ServiceFileHidingClassLoader(originalClassLoader));
            Log log = LogFactory.getLog("direct-jdk-logger");
            log.info("ready");

            boolean simpleFormatterInstalled = Arrays.stream(rootLogger.getHandlers())
                    .filter(ConsoleHandler.class::isInstance)
                    .map(Handler::getFormatter)
                    .anyMatch(SimpleFormatter.class::isInstance);

            assertThat(log.isInfoEnabled()).isTrue();
            assertThat(simpleFormatterInstalled).isTrue();
        } finally {
            restoreProperty("java.util.logging.config.class", configClass);
            restoreProperty("java.util.logging.config.file", configFile);
            currentThread.setContextClassLoader(originalClassLoader);
            directLogger.setLevel(previousLevel);
            rootLogger.removeHandler(consoleHandler);
            consoleHandler.close();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class ServiceFileHidingClassLoader extends ClassLoader {
        private static final String LOG_SERVICE = "META-INF/services/org.apache.juli.logging.Log";

        private ServiceFileHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (LOG_SERVICE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }

        @Override
        public URL getResource(String name) {
            if (LOG_SERVICE.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }
    }

    private static final class MarkerFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage();
        }
    }
}
