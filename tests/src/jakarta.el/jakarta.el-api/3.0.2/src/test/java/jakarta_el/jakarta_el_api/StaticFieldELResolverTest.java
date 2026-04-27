/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.StaticFieldELResolver;
import javax.el.VariableMapper;
import org.junit.jupiter.api.Test;

public class StaticFieldELResolverTest {

    @Test
    void readsPublicStaticFieldValue() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();

        Object value = resolver.getValue(context, new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(value).isEqualTo("hello");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void resolvesPublicStaticFieldType() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();

        Class<?> type = resolver.getType(context, new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(type).isEqualTo(String.class);
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static final class StaticFieldTarget {
        public static final String GREETING = "hello";

        private StaticFieldTarget() {
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
