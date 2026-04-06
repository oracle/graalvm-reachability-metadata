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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryFinderTest {

    private static final String EXPRESSION_FACTORY_PROPERTY = ExpressionFactory.class.getName();

    @Test
    void createsExpressionFactoryUsingContextClassLoader() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        String originalProvider = System.getProperty(EXPRESSION_FACTORY_PROPERTY);

        try {
            Thread.currentThread().setContextClassLoader(FactoryFinderTest.class.getClassLoader());
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, NoArgExpressionFactory.class.getName());

            ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

            assertThat(expressionFactory).isInstanceOf(NoArgExpressionFactory.class);
        }
        finally {
            restoreExpressionFactoryProperty(originalProvider);
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void createsExpressionFactoryUsingSystemClassLoaderAndPropertiesConstructor() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        String originalProvider = System.getProperty(EXPRESSION_FACTORY_PROPERTY);
        Properties properties = new Properties();
        properties.setProperty("factory.mode", "properties");

        try {
            Thread.currentThread().setContextClassLoader(null);
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, PropertiesExpressionFactory.class.getName());

            ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

            assertThat(expressionFactory).isInstanceOf(PropertiesExpressionFactory.class);
            assertThat(((PropertiesExpressionFactory) expressionFactory).getProperties()).isSameAs(properties);
        }
        finally {
            restoreExpressionFactoryProperty(originalProvider);
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void restoreExpressionFactoryProperty(String originalProvider) {
        if (originalProvider == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        }
        else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, originalProvider);
        }
    }

    private abstract static class StubExpressionFactory extends ExpressionFactory {

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

    public static final class NoArgExpressionFactory extends StubExpressionFactory {

        public NoArgExpressionFactory() {
        }
    }

    public static final class PropertiesExpressionFactory extends StubExpressionFactory {

        private final Properties properties;

        public PropertiesExpressionFactory(Properties properties) {
            this.properties = properties;
        }

        Properties getProperties() {
            return this.properties;
        }

    }
}
