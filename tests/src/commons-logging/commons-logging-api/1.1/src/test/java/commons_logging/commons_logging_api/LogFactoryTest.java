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

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    @Test
    void createsSystemPropertyFactoryWithContextClassLoader() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousFactoryProperty = System.getProperty(LogFactory.FACTORY_PROPERTY);
        Thread.currentThread().setContextClassLoader(LogFactory.class.getClassLoader());
        System.setProperty(LogFactory.FACTORY_PROPERTY, LogFactory.FACTORY_DEFAULT);
        LogFactory.releaseAll();

        try {
            LogFactory factory = LogFactory.getFactory();

            assertThat(factory).isInstanceOf(LogFactoryImpl.class);
            assertThat(factory.getInstance("context.loader.factory")).isNotNull();
        } finally {
            LogFactory.releaseAll();
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(LogFactory.FACTORY_PROPERTY, previousFactoryProperty);
        }
    }

    @Test
    void createsSystemPropertyFactoryWhenContextClassLoaderIsNull() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousFactoryProperty = System.getProperty(LogFactory.FACTORY_PROPERTY);
        Thread.currentThread().setContextClassLoader(null);
        System.setProperty(LogFactory.FACTORY_PROPERTY, LogFactory.FACTORY_DEFAULT);
        LogFactory.releaseAll();

        try {
            LogFactory factory = LogFactory.getFactory();

            assertThat(factory).isInstanceOf(LogFactoryImpl.class);
            assertThat(factory.getInstance("fallback.factory")).isNotNull();
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
}
