/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import javax.el.BeanELResolver;
import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.StaticFieldELResolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELUtilTest {

    @Test
    void invokesConstructorWhenParameterTypesAreProvided() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                newContext(),
                new ELClass(ConstructorTarget.class),
                "<init>",
                new Class<?>[] { String.class },
                new Object[] { "typed" });

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).getLabel()).isEqualTo("ctor:typed");
    }

    @Test
    void invokesConstructorWhenParameterTypesAreInferredFromArguments() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                newContext(),
                new ELClass(ConstructorTarget.class),
                "<init>",
                null,
                new Object[] { "inferred" });

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).getLabel()).isEqualTo("ctor:inferred");
    }

    @Test
    void invokesBeanMethodWhenParameterTypesAreProvided() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "describeInput",
                new Class<?>[] { String.class },
                new Object[] { "typed" });

        assertThat(value).isEqualTo("input:typed");
    }

    @Test
    void invokesBeanMethodWhenParameterTypesAreInferredFromArguments() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "describeInput",
                null,
                new Object[] { "inferred" });

        assertThat(value).isEqualTo("input:inferred");
    }

    private static ELContext newContext() {
        return new ELManager().getELContext();
    }

    public static final class ConstructorTarget {
        private final String label;

        public ConstructorTarget(String value) {
            this.label = "ctor:" + value;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class MethodTarget {
        public String describeInput(String value) {
            return "input:" + value;
        }
    }
}
