/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import org.springframework.boot.logging.LoggingSystem;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class LoggingSystemTest {

    @Test
    void getInstantiatesConfiguredLoggingSystem() {
        String previousLoggingSystem = System.getProperty(LoggingSystem.SYSTEM_PROPERTY);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, TestLoggingSystem.class.getName());
        try {
            LoggingSystem loggingSystem = LoggingSystem.get(classLoader);

            assertThat(loggingSystem).isInstanceOf(TestLoggingSystem.class);
            assertThat(((TestLoggingSystem) loggingSystem).getClassLoader()).isSameAs(classLoader);
        }
        finally {
            restoreLoggingSystemProperty(previousLoggingSystem);
        }
    }

    private static void restoreLoggingSystemProperty(String previousLoggingSystem) {
        if (previousLoggingSystem != null) {
            System.setProperty(LoggingSystem.SYSTEM_PROPERTY, previousLoggingSystem);
        }
        else {
            System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
        }
    }

    public static final class TestLoggingSystem extends LoggingSystem {

        private final ClassLoader classLoader;

        public TestLoggingSystem(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        ClassLoader getClassLoader() {
            return this.classLoader;
        }

        @Override
        public void beforeInitialize() {

        }

    }

}
