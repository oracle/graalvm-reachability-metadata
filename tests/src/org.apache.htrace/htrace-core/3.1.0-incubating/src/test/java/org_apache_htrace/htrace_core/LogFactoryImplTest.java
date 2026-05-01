/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.commons.logging.Log;
import org.apache.htrace.commons.logging.LogConfigurationException;
import org.apache.htrace.commons.logging.LogFactory;
import org.apache.htrace.commons.logging.impl.LogFactoryImpl;
import org.apache.htrace.commons.logging.impl.NoOpLog;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LogFactoryImplTest {
    static {
        System.setProperty("org.apache.htrace.commons.logging.diagnostics.dest", "STDERR");
    }

    @Test
    void loadsConfiguredLogClassThroughFallbackLoaderAndReusesCachedConstructor() {
        try {
            ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(new IsolatedClassLoader());
            try {
                LogFactoryImpl factory = new LogFactoryImpl();
                factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, RecordingLog.class.getName());

                Log first = factory.getInstance("first-category");
                Log second = factory.getInstance("second-category");

                assertThat(first).isInstanceOf(RecordingLog.class);
                assertThat(second).isInstanceOf(RecordingLog.class);
                assertThat(((RecordingLog) first).name).isEqualTo("first-category");
                assertThat(((RecordingLog) second).name).isEqualTo("second-category");
                assertThat(((RecordingLog) first).logFactory).isSameAs(factory);
                assertThat(((RecordingLog) second).logFactory).isSameAs(factory);
            } finally {
                Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeature(error);
        }
    }

    @Test
    void walksToSystemClassLoaderResourcesWhenConfiguredClassIsNotALog() {
        try {
            ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(new IsolatedClassLoader());
            try {
                LogFactoryImpl factory = new LogFactoryImpl();
                factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, String.class.getName());

                assertThatThrownBy(() -> factory.getInstance("not-a-log-category"))
                        .isInstanceOf(LogConfigurationException.class)
                        .hasMessageContaining(String.class.getName())
                        .hasMessageContaining("cannot be found or is not useable");
            } finally {
                Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeature(error);
        }
    }

    private static void rethrowUnlessUnsupportedFeature(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader() {
            super(null);
        }
    }

    public static class RecordingLog extends NoOpLog {
        private final String name;
        private LogFactory logFactory;

        public RecordingLog(String name) {
            super(name);
            this.name = name;
        }

        public void setLogFactory(LogFactory logFactory) {
            this.logFactory = logFactory;
        }
    }
}
