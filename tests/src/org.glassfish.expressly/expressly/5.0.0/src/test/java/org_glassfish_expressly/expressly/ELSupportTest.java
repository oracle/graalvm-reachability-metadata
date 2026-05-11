/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_expressly.expressly;

import java.util.List;
import java.util.function.Function;

import org.glassfish.expressly.ExpressionFactoryImpl;
import org.glassfish.expressly.lang.ELSupport;
import org.junit.jupiter.api.Test;

import jakarta.el.ELContext;
import jakarta.el.LambdaExpression;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;

import static org.assertj.core.api.Assertions.assertThat;

public class ELSupportTest {

    @Test
    void coercesArrayElementsToRequestedComponentType() {
        ELContext context = new StandardELContext(new ExpressionFactoryImpl());

        Integer[] value = ELSupport.coerceToType(context, new String[] { "1", "2", "3" }, Integer[].class);

        assertThat(value).containsExactly(1, 2, 3);
    }

    @Test
    void coercesLambdaExpressionToFunctionalInterfaceProxy() {
        ELContext context = new StandardELContext(new ExpressionFactoryImpl());
        LambdaExpression lambdaExpression = new LambdaExpression(List.of("value"), new ConstantValueExpression("mapped"));

        @SuppressWarnings("unchecked")
        Function<String, String> function = ELSupport.coerceToType(context, lambdaExpression, Function.class);

        assertThat(function.apply("ignored")).isEqualTo("mapped");
    }

    private static final class ConstantValueExpression extends ValueExpression {
        private static final long serialVersionUID = 1L;

        private final Object value;

        private ConstantValueExpression(Object value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue(ELContext context) {
            return (T) value;
        }

        @Override
        public void setValue(ELContext context, Object value) {
            throw new UnsupportedOperationException("Constant value expressions are read-only");
        }

        @Override
        public boolean isReadOnly(ELContext context) {
            return true;
        }

        @Override
        public Class<?> getType(ELContext context) {
            return value.getClass();
        }

        @Override
        public Class<?> getExpectedType() {
            return Object.class;
        }

        @Override
        public String getExpressionString() {
            return value.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ConstantValueExpression)) {
                return false;
            }
            ConstantValueExpression other = (ConstantValueExpression) obj;
            return value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean isLiteralText() {
            return true;
        }
    }
}
