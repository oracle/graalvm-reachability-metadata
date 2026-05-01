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

public class LogFactoryAnonymous3Test {
    @Test
    void fallsBackToSystemResourceLookupWhenContextClassLoaderIsNull() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousFactoryProperty = System.getProperty(LogFactory.FACTORY_PROPERTY);
        Thread.currentThread().setContextClassLoader(null);
        System.clearProperty(LogFactory.FACTORY_PROPERTY);
        LogFactory.releaseAll();

        try {
            LogFactory factory = LogFactory.getFactory();

            assertThat(factory).isInstanceOf(LogFactoryImpl.class);
        } catch (Error error) {
            rethrowUnlessUnsupportedFeature(error);
        } finally {
            LogFactory.releaseAll();
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(LogFactory.FACTORY_PROPERTY, previousFactoryProperty);
        }
    }

    private static void rethrowUnlessUnsupportedFeature(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }
}
