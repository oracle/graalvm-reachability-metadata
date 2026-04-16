/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.StaticFieldELResolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticFieldELResolverTest {

    @Test
    void readsPublicStaticFieldValue() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.getValue(newContext(), new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(value).isEqualTo("hello");
    }

    @Test
    void resolvesPublicStaticFieldType() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Class<?> type = resolver.getType(newContext(), new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(type).isEqualTo(String.class);
    }

    @Test
    void invokesConstructorWhenParameterTypesAreProvided() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                newContext(),
                new ELClass(ConstructorTarget.class),
                "<init>",
                new Class<?>[] {String.class},
                new Object[] {"typed"});

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
                new Object[] {"inferred"});

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).getLabel()).isEqualTo("ctor:inferred");
    }

    private static ELContext newContext() {
        return new ELManager().getELContext();
    }

    public static final class StaticFieldTarget {
        public static final String GREETING = "hello";

        private StaticFieldTarget() {
        }
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
}
