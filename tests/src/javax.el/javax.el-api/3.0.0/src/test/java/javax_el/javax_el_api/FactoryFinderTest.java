/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import java.util.Properties;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private static final String EXPRESSION_FACTORY_PROPERTY = "javax.el.ExpressionFactory";

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalExpressionFactoryProperty = System.getProperty(EXPRESSION_FACTORY_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalExpressionFactoryProperty == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        } else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, originalExpressionFactoryProperty);
        }
    }

    @Test
    void loadsProviderFromSystemResourcesWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);
        System.clearProperty(EXPRESSION_FACTORY_PROPERTY);

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

        assertThat(expressionFactory).isInstanceOf(ServiceExpressionFactory.class);
        ServiceExpressionFactory serviceExpressionFactory = (ServiceExpressionFactory) expressionFactory;
        assertThat(serviceExpressionFactory.getConstructorMode()).isEqualTo("default");
        assertThat(serviceExpressionFactory.getProperties()).isNull();
    }

    @Test
    void loadsProviderFromContextClassLoaderUsingPropertiesConstructor() {
        ClassLoader testClassLoader = FactoryFinderTest.class.getClassLoader();
        currentThread.setContextClassLoader(testClassLoader);
        System.clearProperty(EXPRESSION_FACTORY_PROPERTY);

        Properties properties = new Properties();
        properties.setProperty("javax.el.cacheSize", "32");

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

        assertThat(expressionFactory).isInstanceOf(ServiceExpressionFactory.class);
        ServiceExpressionFactory serviceExpressionFactory = (ServiceExpressionFactory) expressionFactory;
        assertThat(serviceExpressionFactory.getConstructorMode()).isEqualTo("properties");
        assertThat(serviceExpressionFactory.getProperties()).isSameAs(properties);
        assertThat(serviceExpressionFactory.getProperties()).containsEntry("javax.el.cacheSize", "32");
    }

    public static final class ServiceExpressionFactory extends ExpressionFactory {
        private final String constructorMode;
        private final Properties properties;

        public ServiceExpressionFactory() {
            this.constructorMode = "default";
            this.properties = null;
        }

        public ServiceExpressionFactory(Properties properties) {
            this.constructorMode = "properties";
            this.properties = properties;
        }

        public String getConstructorMode() {
            return constructorMode;
        }

        public Properties getProperties() {
            return properties;
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            return null;
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            return null;
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context, String expression, Class<?> expectedReturnType, Class<?>[] expectedParamTypes) {
            return null;
        }

        @Override
        public Object coerceToType(Object obj, Class<?> targetType) {
            return obj;
        }
    }
}
