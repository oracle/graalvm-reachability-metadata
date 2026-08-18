/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpressionFactoryTest {
    private static final String EXPRESSION_FACTORY_PROPERTY = "javax.el.ExpressionFactory";

    private String originalExpressionFactoryProperty;

    @BeforeEach
    void rememberExpressionFactoryProperty() {
        originalExpressionFactoryProperty = System.getProperty(EXPRESSION_FACTORY_PROPERTY);
    }

    @AfterEach
    void restoreExpressionFactoryProperty() {
        if (originalExpressionFactoryProperty == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        } else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, originalExpressionFactoryProperty);
        }
    }

    @Test
    void newInstanceWithPropertiesUsesPropertiesConstructor() {
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, ConfigurableExpressionFactory.class.getName());
        Properties properties = new Properties();
        properties.setProperty("javax.el.cacheSize", "32");

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

        assertThat(expressionFactory).isInstanceOf(ConfigurableExpressionFactory.class);
        ConfigurableExpressionFactory configurableExpressionFactory = (ConfigurableExpressionFactory) expressionFactory;
        assertThat(configurableExpressionFactory.getConstructorMode()).isEqualTo("properties");
        assertThat(configurableExpressionFactory.getProperties()).isSameAs(properties);
        assertThat(configurableExpressionFactory.getProperties()).containsEntry("javax.el.cacheSize", "32");
    }

    public static final class ConfigurableExpressionFactory extends ExpressionFactory {
        private final String constructorMode;
        private final Properties properties;

        public ConfigurableExpressionFactory() {
            this.constructorMode = "default";
            this.properties = null;
        }

        public ConfigurableExpressionFactory(Properties properties) {
            this.constructorMode = "properties";
            this.properties = properties;
        }

        String getConstructorMode() {
            return constructorMode;
        }

        Properties getProperties() {
            return properties;
        }

        @Override
        public Object coerceToType(Object obj, Class<?> expectedType) throws ELException {
            if (obj == null || expectedType.isInstance(obj)) {
                return obj;
            }
            if (expectedType == String.class) {
                return obj.toString();
            }
            throw new ELException("Unsupported coercion to " + expectedType.getName());
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MethodExpression createMethodExpression(ELContext context, String expression,
                Class<?> expectedReturnType, Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException();
        }
    }
}
