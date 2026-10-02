/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.util.Properties;

import jakarta.el.ELContext;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaxElExpressionFactoryTest {
    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void captureThreadState() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restoreThreadState() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void loadsServiceProviderWithFallbackClassLoadingWhenContextClassLoaderIsNull() {
        Thread.currentThread().setContextClassLoader(null);

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

        assertThat(expressionFactory).isInstanceOf(PropertiesExpressionFactory.class);
        PropertiesExpressionFactory propertiesFactory = (PropertiesExpressionFactory) expressionFactory;
        assertThat(propertiesFactory.getConstructorMode()).isEqualTo("default");
        assertThat(propertiesFactory.getProperties()).isNull();
        assertThat(propertiesFactory.coerceToType("17", Integer.class)).isEqualTo(17);
    }

    @Test
    void loadsServiceProviderFromContextClassLoaderUsingPropertiesConstructor() {
        Thread.currentThread()
                .setContextClassLoader(new DelegatingClassLoader(getClass().getClassLoader()));
        Properties properties = new Properties();
        properties.setProperty("jakarta.el.cacheSize", "32");

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

        assertThat(expressionFactory).isInstanceOf(PropertiesExpressionFactory.class);
        PropertiesExpressionFactory propertiesFactory = (PropertiesExpressionFactory) expressionFactory;
        assertThat(propertiesFactory.getConstructorMode()).isEqualTo("properties");
        assertThat(propertiesFactory.getProperties()).isSameAs(properties);
        assertThat(propertiesFactory.coerceToType("true", Boolean.class)).isEqualTo(true);
    }

    public static final class PropertiesExpressionFactory extends ExpressionFactory {
        private final ExpressionFactory delegate = new ExpressionFactoryImpl();
        private final String constructorMode;
        private final Properties properties;

        public PropertiesExpressionFactory() {
            constructorMode = "default";
            properties = null;
        }

        public PropertiesExpressionFactory(Properties properties) {
            constructorMode = "properties";
            this.properties = properties;
        }

        public String getConstructorMode() {
            return constructorMode;
        }

        public Properties getProperties() {
            return properties;
        }

        @Override
        public ValueExpression createValueExpression(
                ELContext context, String expression, Class<?> expectedType) {
            return delegate.createValueExpression(context, expression, expectedType);
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            return delegate.createValueExpression(instance, expectedType);
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context,
                String expression,
                Class<?> expectedReturnType,
                Class<?>[] expectedParamTypes) {
            return delegate.createMethodExpression(
                    context, expression, expectedReturnType, expectedParamTypes);
        }

        @Override
        public Object coerceToType(Object obj, Class<?> expectedType) {
            return delegate.coerceToType(obj, expectedType);
        }
    }

    private static final class DelegatingClassLoader extends ClassLoader {
        private DelegatingClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
