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

@Order(0)
public class LogFactoryTest {

    @Test
    void createsDiscoveredLoggerWithRequestedName() {
        Log log = LogFactory.getLog("factory-created-logger");

        assertThat(log).isInstanceOf(TestLog.class);
        TestLog testLog = (TestLog) log;
        testLog.info("ready");

        assertThat(testLog.getName()).isEqualTo("factory-created-logger");
        assertThat(testLog.getMessage()).isEqualTo("ready");
    }

    public static final class TestLog implements Log {
        private final String name;
        private Object message;

        public TestLog() {
            this("service-provider");
        }

        public TestLog(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Object getMessage() {
            return message;
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
        public void trace(Object value) {
            message = value;
        }

        @Override
        public void trace(Object value, Throwable throwable) {
            message = value;
        }

        @Override
        public void debug(Object value) {
            message = value;
        }

        @Override
        public void debug(Object value, Throwable throwable) {
            message = value;
        }

        @Override
        public void info(Object value) {
            message = value;
        }

        @Override
        public void info(Object value, Throwable throwable) {
            message = value;
        }

        @Override
        public void warn(Object value) {
            message = value;
        }

        @Override
        public void warn(Object value, Throwable throwable) {
            message = value;
        }

        @Override
        public void error(Object value) {
            message = value;
        }

        @Override
        public void error(Object value, Throwable throwable) {
            message = value;
        }

        @Override
        public void fatal(Object value) {
            message = value;
        }

        @Override
        public void fatal(Object value, Throwable throwable) {
            message = value;
        }
    }
}
