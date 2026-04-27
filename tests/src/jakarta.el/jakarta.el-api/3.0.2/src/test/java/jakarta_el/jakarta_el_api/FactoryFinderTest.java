/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
    void loadsProviderWithPropertiesWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, PropertiesExpressionFactory.class.getName());

        Properties properties = new Properties();
        properties.setProperty("javax.el.cacheSize", "64");

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

        assertThat(expressionFactory).isInstanceOf(PropertiesExpressionFactory.class);
        PropertiesExpressionFactory propertiesExpressionFactory = (PropertiesExpressionFactory) expressionFactory;
        assertThat(propertiesExpressionFactory.getProperties()).isSameAs(properties);
        assertThat(propertiesExpressionFactory.getProperties()).containsEntry("javax.el.cacheSize", "64");
    }

    public static final class PropertiesExpressionFactory extends ExpressionFactory {
        private final Properties properties;

        public PropertiesExpressionFactory(Properties properties) {
            this.properties = properties;
        }

        public Properties getProperties() {
            return properties;
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException("Expression parsing is not used by these tests");
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException("Value expressions are not used by these tests");
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context,
                String expression,
                Class<?> expectedReturnType,
                Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException("Method expressions are not used by these tests");
        }

        @Override
        public Object coerceToType(Object obj, Class<?> targetType) {
            return obj;
        }
    }
}
