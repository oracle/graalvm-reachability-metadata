/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_logging.commons_logging_api;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.LogFactoryImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryAnonymous3Test {
    private static final String SERVICE_RESOURCE = "META-INF/services/org.apache.commons.logging.LogFactory";

    @Test
    void readsFactoryServiceResourceFromContextClassLoader() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousFactoryProperty = System.getProperty(LogFactory.FACTORY_PROPERTY);
        ServiceResourceClassLoader serviceResourceClassLoader =
                new ServiceResourceClassLoader(previousContextClassLoader);
        Thread.currentThread().setContextClassLoader(serviceResourceClassLoader);
        System.clearProperty(LogFactory.FACTORY_PROPERTY);
        LogFactory.releaseAll();

        try {
            LogFactory factory = LogFactory.getFactory();

            assertThat(factory).isInstanceOf(LogFactoryImpl.class);
            assertThat(serviceResourceClassLoader.serviceResourceRequests).isEqualTo(1);
        } finally {
            LogFactory.releaseAll();
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(LogFactory.FACTORY_PROPERTY, previousFactoryProperty);
        }
    }

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
        } finally {
            LogFactory.releaseAll();
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(LogFactory.FACTORY_PROPERTY, previousFactoryProperty);
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static final class ServiceResourceClassLoader extends ClassLoader {
        private int serviceResourceRequests;

        private ServiceResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (SERVICE_RESOURCE.equals(name)) {
                serviceResourceRequests++;
                return new ByteArrayInputStream((LogFactory.FACTORY_DEFAULT + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
