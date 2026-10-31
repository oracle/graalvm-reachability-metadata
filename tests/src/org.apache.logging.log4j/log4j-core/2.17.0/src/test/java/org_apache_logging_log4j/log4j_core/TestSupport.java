/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

final class TestSupport {
    private TestSupport() {
    }

    static LogEvent event(String message) {
        return Log4jLogEvent.newBuilder().setLoggerName("application.worker")
                .setLevel(Level.INFO).setMessage(new SimpleMessage(message)).build();
    }

    static XmlConfiguration configure(LoggerContext context, String xml) throws IOException {
        ConfigurationSource source = new ConfigurationSource(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        XmlConfiguration configuration = new XmlConfiguration(context, source);
        context.start(configuration);
        return configuration;
    }

    static <T> T withProperty(String key, String value, Supplier<T> action) {
        String previous = System.setProperty(key, value);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    static final class RecordingAppender extends AbstractAppender {
        private LogEvent lastEvent;

        RecordingAppender() {
            super("recording", null, null, false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lastEvent = event.toImmutable();
        }

        LogEvent lastEvent() {
            return lastEvent;
        }
    }
}
