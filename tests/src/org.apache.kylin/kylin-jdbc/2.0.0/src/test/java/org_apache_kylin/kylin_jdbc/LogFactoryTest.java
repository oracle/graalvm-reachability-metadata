/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.Log;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogFactory;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.LogFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    @Test
    void discoversAFactoryAndCreatesANamedLogger() {
        LogFactory.releaseAll();
        try {
            Log first = LogFactory.getFactory().getInstance("kylin.connection");
            Log second = LogFactory.getLog("kylin.query");

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
        } finally {
            LogFactory.releaseAll();
        }
    }

    @Test
    void fallsBackToTheLibraryLoaderForAFactoryImplementation() {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        String originalFactory = System.getProperty(LogFactory.FACTORY_PROPERTY);
        LogFactory.releaseAll();
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            System.setProperty(LogFactory.FACTORY_PROPERTY, LogFactoryImpl.class.getName());
            LogFactory factory = LogFactory.getFactory();
            assertThat(factory).isInstanceOf(LogFactoryImpl.class);
            assertThat(factory.getInstance("fallback.logger")).isNotNull();
        } finally {
            LogFactory.releaseAll();
            thread.setContextClassLoader(originalLoader);
            if (originalFactory == null) {
                System.clearProperty(LogFactory.FACTORY_PROPERTY);
            } else {
                System.setProperty(LogFactory.FACTORY_PROPERTY, originalFactory);
            }
        }
    }
}
