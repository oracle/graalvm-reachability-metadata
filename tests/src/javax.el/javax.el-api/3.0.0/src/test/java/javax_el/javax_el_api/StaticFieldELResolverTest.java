/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.StandardELContext;
import javax.el.StaticFieldELResolver;
import javax.el.ValueExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticFieldELResolverTest {

    private static final String EXPRESSION_FACTORY_PROPERTY = ExpressionFactory.class.getName();

    private ClassLoader originalContextClassLoader;

    private String originalProvider;

    @BeforeEach
    void setUpExpressionFactoryProvider() {
        this.originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        this.originalProvider = System.getProperty(EXPRESSION_FACTORY_PROPERTY);

        Thread.currentThread().setContextClassLoader(StaticFieldELResolverTest.class.getClassLoader());
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, StubExpressionFactory.class.getName());
    }

    @AfterEach
    void restoreExpressionFactoryProvider() {
        if (this.originalProvider == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        } else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, this.originalProvider);
        }
        Thread.currentThread().setContextClassLoader(this.originalContextClassLoader);
    }

    @Test
    void getTypeReturnsPublicStaticFieldType() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        StandardELContext context = createContext();

        Class<?> type = resolver.getType(context, new ELClass(StaticFieldLibrary.class), "MESSAGE");

        assertThat(type).isEqualTo(String.class);
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void getValueReturnsPublicStaticFieldValue() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        StandardELContext context = createContext();

        Object value = resolver.getValue(context, new ELClass(StaticFieldLibrary.class), "MESSAGE");

        assertThat(value).isEqualTo(StaticFieldLibrary.MESSAGE);
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesConstructorWithExplicitParameterTypes() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        StandardELContext context = createContext();

        Object instance = resolver.invoke(
                context,
                new ELClass(ExplicitConstruction.class),
                "<init>",
                new Class<?>[]{String.class, int.class},
                new Object[]{"alpha", 7}
        );

        assertThat(instance).isInstanceOf(ExplicitConstruction.class);
        assertThat(((ExplicitConstruction) instance).getLabel()).isEqualTo("alpha");
        assertThat(((ExplicitConstruction) instance).getCount()).isEqualTo(7);
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesConstructorWhenParameterTypesAreInferred() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        StandardELContext context = createContext();

        Object instance = resolver.invoke(
                context,
                new ELClass(InferredConstruction.class),
                "<init>",
                null,
                new Object[]{"beta"}
        );

        assertThat(instance).isInstanceOf(InferredConstruction.class);
        assertThat(((InferredConstruction) instance).getLabel()).isEqualTo("beta");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesStaticMethodWithExplicitParameterTypes() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        StandardELContext context = createContext();

        Object result = resolver.invoke(
                context,
                new ELClass(StaticLibrary.class),
                "multiply",
                new Class<?>[]{int.class, int.class},
                new Object[]{6, 7}
        );

        assertThat(result).isEqualTo(42);
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

    public static final class ExplicitConstruction {

        private final String label;

        private final int count;

        ExplicitConstruction(String label, int count) {
            this.label = label;
            this.count = count;
        }

        String getLabel() {
            return this.label;
        }

        int getCount() {
            return this.count;
        }
    }

    public static final class InferredConstruction {

        private final String label;

        InferredConstruction(String label) {
            this.label = label;
        }

        String getLabel() {
            return this.label;
        }
    }

    public static final class StaticFieldLibrary {

        public static final String MESSAGE = "resolved";
    }

    public static final class StaticLibrary {

        public static int multiply(int left, int right) {
            return left * right;
        }
    }

}
