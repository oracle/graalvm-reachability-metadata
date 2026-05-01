/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.commons.logging.LogFactory;
import org.apache.htrace.commons.logging.impl.LogFactoryImpl;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    @Test
    void fallsBackToFactoryClassLoaderWhenProvidedLoaderCannotFindFactoryClass() {
        try {
            LogFactory factory = ExposedLogFactory.create(LogFactoryImpl.class.getName(), new EmptyClassLoader());

            assertThat(factory).isInstanceOf(LogFactoryImpl.class);
            assertThat(factory.getClass().getClassLoader()).isSameAs(LogFactoryImpl.class.getClassLoader());
        } catch (Error error) {
            rethrowUnlessUnsupportedFeature(error);
        }
    }

    private static void rethrowUnlessUnsupportedFeature(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private abstract static class ExposedLogFactory extends LogFactory {
        private static LogFactory create(String factoryClass, ClassLoader classLoader) {
            return newFactory(factoryClass, classLoader);
        }
    }

    private static final class EmptyClassLoader extends ClassLoader {
        private EmptyClassLoader() {
            super(null);
        }
    }
}
