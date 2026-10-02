/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.util.Properties;

import jakarta.el.ExpressionFactory;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaxElExpressionFactoryTest {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void loadsBundledProviderWithFallbackClassLoadingWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

        assertThat(expressionFactory).isInstanceOf(ExpressionFactoryImpl.class);
        assertThat(expressionFactory.coerceToType("17", Integer.class)).isEqualTo(17);
    }

    @Test
    void loadsBundledProviderFromContextClassLoaderWhenPropertiesAreProvided() {
        currentThread.setContextClassLoader(new DelegatingClassLoader(getClass().getClassLoader()));
        Properties properties = new Properties();
        properties.setProperty("jakarta.el.cacheSize", "32");

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

        assertThat(expressionFactory).isInstanceOf(ExpressionFactoryImpl.class);
        assertThat(expressionFactory.coerceToType("true", Boolean.class)).isEqualTo(true);
    }

    private static final class DelegatingClassLoader extends ClassLoader {
        private DelegatingClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
