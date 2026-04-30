/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

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
        TestELContext context = new TestELContext();

        Object value = resolver.getValue(context, new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(value).isEqualTo("hello");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void resolvesPublicStaticFieldType() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        TestELContext context = new TestELContext();

        Class<?> type = resolver.getType(context, new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(type).isEqualTo(String.class);
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static final class StaticFieldTarget {
        public static final String GREETING = "hello";

        private StaticFieldTarget() {
        }
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver = new NoOpELResolver();

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
    }

    private static final class NoOpELResolver extends ELResolver {
        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            return true;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return null;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }
    }
}
