/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.StandardELContext;
import javax.el.ValueExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanELResolverTest {

    private static final String EXPRESSION_FACTORY_PROPERTY = ExpressionFactory.class.getName();

    private ClassLoader originalContextClassLoader;

    private String originalProvider;

    @BeforeEach
    void setUpExpressionFactoryProvider() {
        this.originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        this.originalProvider = System.getProperty(EXPRESSION_FACTORY_PROPERTY);

        Thread.currentThread().setContextClassLoader(BeanELResolverTest.class.getClassLoader());
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, StubExpressionFactory.class.getName());
    }

    @AfterEach
    void restoreExpressionFactoryProvider() {
        if (this.originalProvider == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        }
        else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, this.originalProvider);
        }
        Thread.currentThread().setContextClassLoader(this.originalContextClassLoader);
    }

    @Test
    void invokesInstanceMethodWhenParameterTypesAreInferred() {
        BeanELResolver resolver = new BeanELResolver();
        StandardELContext context = createContext();

        Object result = resolver.invoke(
                context,
                new InstanceLibrary(),
                "join",
                null,
                new Object[] { "left", "right" }
        );

        assertThat(result).isEqualTo("left:right");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void readsAndWritesPropertyDeclaredOnPublicInterfaceFromPackagePrivateBean() {
        BeanELResolver resolver = new BeanELResolver();
        StandardELContext context = createContext();
        InterfaceBackedBean bean = new InterfaceBackedBean("before");

        Object initialValue = resolver.getValue(context, bean, "value");

        assertThat(initialValue).isEqualTo("before");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, bean, "value", "after");

        assertThat(bean.currentValue()).isEqualTo("after");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void readsAndWritesPropertyInheritedFromPublicSuperclassOnPackagePrivateBean() {
        BeanELResolver resolver = new BeanELResolver();
        StandardELContext context = createContext();
        SuperclassBackedBean bean = new SuperclassBackedBean("start");

        Object initialValue = resolver.getValue(context, bean, "label");

        assertThat(initialValue).isEqualTo("start");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, bean, "label", "updated");

        assertThat(bean.getLabel()).isEqualTo("updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    private static StandardELContext createContext() {
        return new StandardELContext(new StubExpressionFactory());
    }

    public static final class StubExpressionFactory extends ExpressionFactory {

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

    public static final class InstanceLibrary {

        public String join(String left, String right) {
            return left + ":" + right;
        }
    }

    public interface ValueContract {

        String getValue();

        void setValue(String value);
    }

    static final class InterfaceBackedBean implements ValueContract {

        private String value;

        InterfaceBackedBean(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return this.value;
        }

        @Override
        public void setValue(String value) {
            this.value = value;
        }

        String currentValue() {
            return this.value;
        }
    }

    public static class LabelBase {

        private String label;

        public LabelBase(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    static final class SuperclassBackedBean extends LabelBase {

        SuperclassBackedBean(String label) {
            super(label);
        }
    }
}
