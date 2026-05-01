/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.el_api;

import javax.el.ArrayELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.el.VariableMapper;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArrayELResolverTest {
    @Test
    void reportsIntegerCommonPropertyTypeForArrays() {
        ArrayELResolver resolver = new ArrayELResolver();

        Class<?> propertyType = resolver.getCommonPropertyType(null, new String[] {"first", "second"});

        assertThat(propertyType).isSameAs(Integer.class);
        assertThat(resolver.getCommonPropertyType(null, "not an array")).isNull();
    }

    @Test
    void readsAndWritesArrayElementsUsingCoercedIndexes() {
        ArrayELResolver resolver = new ArrayELResolver();
        TestELContext context = new TestELContext(resolver);
        String[] values = new String[] {"zero", "one", "two"};

        Object value = resolver.getValue(context, values, "1");

        assertThat(value).isEqualTo("one");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, values, Long.valueOf(2L), "updated");

        assertThat(values).containsExactly("zero", "one", "updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void reportsComponentTypeAndReadOnlyStateForArrayElements() {
        ArrayELResolver resolver = new ArrayELResolver();
        TestELContext context = new TestELContext(resolver);
        String[] values = new String[] {"zero"};

        Class<?> componentType = resolver.getType(context, values, Boolean.FALSE);

        assertThat(componentType).isSameAs(String.class);
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        assertThat(resolver.isReadOnly(context, values, Integer.valueOf(0))).isFalse();
        assertThat(context.isPropertyResolved()).isTrue();

        ArrayELResolver readOnlyResolver = new ArrayELResolver(true);
        TestELContext readOnlyContext = new TestELContext(readOnlyResolver);
        assertThat(readOnlyResolver.isReadOnly(readOnlyContext, values, Integer.valueOf(0))).isTrue();
    }

    @Test
    void rejectsInvalidArrayAccesses() {
        ArrayELResolver resolver = new ArrayELResolver();
        TestELContext context = new TestELContext(resolver);
        Number[] values = new Number[] {Integer.valueOf(1)};

        assertThatThrownBy(() -> resolver.getType(context, values, Integer.valueOf(3)))
                .isInstanceOf(PropertyNotFoundException.class);
        assertThatThrownBy(() -> resolver.setValue(context, values, Integer.valueOf(0), "not a number"))
                .isInstanceOf(ClassCastException.class);

        ArrayELResolver readOnlyResolver = new ArrayELResolver(true);
        TestELContext readOnlyContext = new TestELContext(readOnlyResolver);
        assertThatThrownBy(() -> readOnlyResolver.setValue(readOnlyContext, values, Integer.valueOf(0), Integer.valueOf(2)))
                .isInstanceOf(PropertyNotWritableException.class);
    }

    @Test
    void returnsNullForUnsupportedBasesAndFeatureDescriptors() {
        ArrayELResolver resolver = new ArrayELResolver();
        TestELContext context = new TestELContext(resolver);

        assertThat(resolver.getValue(context, null, Integer.valueOf(0))).isNull();
        assertThat(context.isPropertyResolved()).isFalse();

        Iterator<FeatureDescriptor> descriptors = resolver.getFeatureDescriptors(context, new String[] {"value"});
        assertThat(descriptors).isNull();
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver;

        TestELContext(ELResolver resolver) {
            this.resolver = resolver;
        }

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
}
