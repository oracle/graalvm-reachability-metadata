/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BeanELResolverTest {

    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty("javax.el.ExpressionFactory", TestExpressionFactory.class.getName());
    }

    @Test
    void getValueInvokesBeanGetter() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        ProfileBean bean = new ProfileBean("Duke");

        Object value = resolver.getValue(context, bean, "displayName");

        assertThat(value).isEqualTo("Duke");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void setValueInvokesBeanSetter() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        ProfileBean bean = new ProfileBean("before");

        resolver.setValue(context, bean, "displayName", "after");

        assertThat(bean.getDisplayName()).isEqualTo("after");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static final class TestExpressionFactory extends ExpressionFactory {

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
            if (obj == null || targetType.isInstance(obj)) {
                return obj;
            }
            if (targetType == String.class) {
                return Objects.toString(obj);
            }
            throw new ELException("Unsupported conversion to " + targetType.getName());
        }

        @Override
        public ELResolver getStreamELResolver() {
            return null;
        }
    }

    public static final class ProfileBean {
        private String displayName;

        public ProfileBean(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class SimpleELContext extends ELContext {

        @Override
        public ELResolver getELResolver() {
            return null;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }
}
