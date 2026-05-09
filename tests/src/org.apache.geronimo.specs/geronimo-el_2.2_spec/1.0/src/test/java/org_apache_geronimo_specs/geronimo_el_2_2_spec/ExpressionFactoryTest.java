/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import java.util.Properties;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionFactoryTest {

    private static final String EXPRESSION_FACTORY_PROPERTY = "javax.el.ExpressionFactory";

    @Test
    void createsConfiguredFactoryWithPropertiesConstructor() {
        PropertiesAwareExpressionFactory.reset();
        String previousFactoryClass = System.getProperty(EXPRESSION_FACTORY_PROPERTY);
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, PropertiesAwareExpressionFactory.class.getName());
        try {
            Properties properties = new Properties();
            properties.setProperty("sample", "value");

            ExpressionFactory factory = ExpressionFactory.newInstance(properties);

            assertThat(factory).isInstanceOf(PropertiesAwareExpressionFactory.class);
            assertThat(PropertiesAwareExpressionFactory.propertiesConstructorInvocations).isEqualTo(1);
            assertThat(PropertiesAwareExpressionFactory.defaultConstructorInvocations).isZero();
            assertThat(PropertiesAwareExpressionFactory.constructorProperties).isSameAs(properties);
        } finally {
            if (previousFactoryClass == null) {
                System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
            } else {
                System.setProperty(EXPRESSION_FACTORY_PROPERTY, previousFactoryClass);
            }
        }
    }

    public static final class PropertiesAwareExpressionFactory extends ExpressionFactory {
        static int defaultConstructorInvocations;
        static int propertiesConstructorInvocations;
        static Properties constructorProperties;

        public PropertiesAwareExpressionFactory() {
            defaultConstructorInvocations++;
        }

        public PropertiesAwareExpressionFactory(Properties properties) {
            propertiesConstructorInvocations++;
            constructorProperties = properties;
        }

        static void reset() {
            defaultConstructorInvocations = 0;
            propertiesConstructorInvocations = 0;
            constructorProperties = null;
        }

        @Override
        public Object coerceToType(Object obj, Class<?> expectedType) throws ELException {
            if (obj == null || expectedType.isInstance(obj)) {
                return obj;
            }
            throw new ELException("Unsupported coercion to " + expectedType.getName());
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException("Value expressions are not used by this test");
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException("Value expressions are not used by this test");
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context,
                String expression,
                Class<?> expectedReturnType,
                Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException("Method expressions are not used by this test");
        }
    }
}
