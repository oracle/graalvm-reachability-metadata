/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import javax.el.BeanELResolver;
import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.PropertyNotFoundException;
import javax.el.StaticFieldELResolver;
import javax.el.TypeConverter;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ELUtilTest {

    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty("javax.el.ExpressionFactory", TestExpressionFactory.class.getName());
    }

    @Test
    void staticResolverInvokesVarargsConstructorOnPublicSuperclass() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();

        Object created = resolver.invoke(
                context,
                new ELClass(HiddenConstructable.class),
                "<init>",
                new Class<?>[] {String.class, String.class, String.class},
                new Object[] {"hello", "native", "image"});

        assertThat(created)
                .isInstanceOf(PublicConstructable.class)
                .isNotInstanceOf(HiddenConstructable.class);
        assertThat(((PublicConstructable) created).message()).isEqualTo("hello:native,image");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void beanResolverInvokesPublicInterfaceMethodImplementedByNonPublicClass() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();

        Object greeting = resolver.invoke(
                context,
                new HiddenInterfaceGreeter(),
                "greet",
                new Class<?>[] {String.class},
                new Object[] {"Duke"});

        assertThat(greeting).isEqualTo("interface:Duke");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void beanResolverInvokesPublicSuperclassMethodFromNonPublicSubclass() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();

        Object description = resolver.invoke(
                context,
                new HiddenMethodChild(),
                "describe",
                new Class<?>[] {String.class},
                new Object[] {"metadata"});

        assertThat(description).isEqualTo("base:metadata");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void missingStaticFieldLoadsLocalizedMessageBundle() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();
        context.setLocale(Locale.ENGLISH);

        assertThatThrownBy(() -> resolver.getValue(context, new ELClass(PublicConstructable.class), "missing"))
                .isInstanceOf(PropertyNotFoundException.class)
                .hasMessageContaining(PublicConstructable.class.getName())
                .hasMessageContaining("missing");
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
            return SimpleELContext.convert(obj, targetType);
        }
    }

    public static class PublicConstructable {
        private final String prefix;
        private final String[] words;

        public PublicConstructable(String prefix, String... words) {
            this.prefix = prefix;
            this.words = words.clone();
        }

        String message() {
            return prefix + ":" + String.join(",", words);
        }
    }

    private static final class HiddenConstructable extends PublicConstructable {

        public HiddenConstructable(String prefix, String... words) {
            super(prefix, words);
        }
    }

    public interface PublicGreeter {

        String greet(String name);
    }

    private static final class HiddenInterfaceGreeter implements PublicGreeter {

        @Override
        public String greet(String name) {
            return "interface:" + name;
        }
    }

    public static class PublicMethodBase {

        public String describe(String value) {
            return "base:" + value;
        }
    }

    private static final class HiddenMethodChild extends PublicMethodBase {
    }

    private static final class SimpleELContext extends ELContext {
        private final ELResolver resolver = new TypeConverter() {
            @Override
            public Object convertToType(ELContext context, Object obj, Class<?> targetType) {
                context.setPropertyResolved(obj, targetType);
                return convert(obj, targetType);
            }
        };

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }

        static Object convert(Object obj, Class<?> targetType) {
            if (obj == null || targetType.isInstance(obj)) {
                return obj;
            }
            if (targetType == String.class) {
                return obj.toString();
            }
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.valueOf(obj.toString());
            }
            throw new ELException("Unsupported conversion to " + targetType.getName());
        }
    }
}
