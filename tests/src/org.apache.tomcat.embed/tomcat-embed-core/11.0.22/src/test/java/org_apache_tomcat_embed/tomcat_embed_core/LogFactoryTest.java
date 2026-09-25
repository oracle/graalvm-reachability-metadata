/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(1)
public class LogFactoryTest {

    @Test
    void createsServiceProvidedLoggerForRequestedName() {
        Log log = LogFactory.getLog("factory-created-logger");

        assertThat(log).isInstanceOf(TestLog.class);
        TestLog testLog = (TestLog) log;
        assertThat(testLog.getName()).isEqualTo("factory-created-logger");

        testLog.info("service provider message");

        assertThat(testLog.getLastMessage()).isEqualTo("service provider message");
        assertThat(log.isInfoEnabled()).isTrue();
        assertThat(log.isDebugEnabled()).isFalse();
    }

    public static final class TestLog implements Log {
        private final String name;
        private Object lastMessage;

        public TestLog(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Object getLastMessage() {
            return lastMessage;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isFatalEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void trace(Object message) {
            record(message);
        }

        @Override
        public void trace(Object message, Throwable throwable) {
            record(message);
        }

        @Override
        public void debug(Object message) {
            record(message);
        }

        @Override
        public void debug(Object message, Throwable throwable) {
            record(message);
        }

        @Override
        public void info(Object message) {
            record(message);
        }

        @Override
        public void info(Object message, Throwable throwable) {
            record(message);
        }

        @Override
        public void warn(Object message) {
            record(message);
        }

        @Override
        public void warn(Object message, Throwable throwable) {
            record(message);
        }

        @Override
        public void error(Object message) {
            record(message);
        }

        @Override
        public void error(Object message, Throwable throwable) {
            record(message);
        }

        @Override
        public void fatal(Object message) {
            record(message);
        }

        @Override
        public void fatal(Object message, Throwable throwable) {
            record(message);
        }

        private void record(Object message) {
            lastMessage = message;
        }
    }
}
