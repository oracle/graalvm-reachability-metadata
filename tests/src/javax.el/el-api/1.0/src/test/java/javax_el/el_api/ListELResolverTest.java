/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.el_api;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.ListELResolver;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.el.VariableMapper;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ListELResolverTest {
    @Test
    void reportsObjectTypeAndIntegerCommonPropertyTypeForLists() {
        ListELResolver resolver = new ListELResolver();
        TestELContext context = new TestELContext(resolver);
        List<String> values = Arrays.asList("zero", "one", "two");

        Class<?> elementType = resolver.getType(context, values, "1");
        Class<?> commonPropertyType = resolver.getCommonPropertyType(context, values);

        assertThat(elementType).isSameAs(Object.class);
        assertThat(context.isPropertyResolved()).isTrue();
        assertThat(commonPropertyType).isSameAs(Integer.class);
        assertThat(resolver.getCommonPropertyType(context, "not a list")).isNull();
    }

    @Test
    void readsAndWritesListElementsUsingCoercedIndexes() {
        ListELResolver resolver = new ListELResolver();
        TestELContext context = new TestELContext(resolver);
        List<String> values = new ArrayList<>(Arrays.asList("zero", "one", "two"));

        Object stringIndexedValue = resolver.getValue(context, values, "1");

        assertThat(stringIndexedValue).isEqualTo("one");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        Object booleanIndexedValue = resolver.getValue(context, values, Boolean.TRUE);

        assertThat(booleanIndexedValue).isEqualTo("one");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, values, Long.valueOf(2L), "updated");

        assertThat(values).containsExactly("zero", "one", "updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void reportsReadOnlyStateForMutableConfiguredAndUnmodifiableLists() {
        List<String> mutableValues = new ArrayList<>(Arrays.asList("zero"));
        ListELResolver resolver = new ListELResolver();
        TestELContext context = new TestELContext(resolver);

        assertThat(resolver.isReadOnly(context, mutableValues, Integer.valueOf(0))).isFalse();
        assertThat(context.isPropertyResolved()).isTrue();

        ListELResolver configuredReadOnlyResolver = new ListELResolver(true);
        TestELContext configuredReadOnlyContext = new TestELContext(configuredReadOnlyResolver);
        assertThat(configuredReadOnlyResolver.isReadOnly(
                configuredReadOnlyContext, mutableValues, Integer.valueOf(0))).isTrue();

        List<String> unmodifiableValues = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("zero")));
        TestELContext unmodifiableContext = new TestELContext(resolver);
        assertThat(resolver.isReadOnly(unmodifiableContext, unmodifiableValues, Integer.valueOf(0))).isTrue();
    }

    @Test
    void rejectsInvalidListAccesses() {
        ListELResolver resolver = new ListELResolver();
        TestELContext context = new TestELContext(resolver);
        List<String> values = new ArrayList<>(Arrays.asList("zero"));

        assertThat(resolver.getValue(context, values, Integer.valueOf(5))).isNull();
        assertThatThrownBy(() -> resolver.getType(context, values, Integer.valueOf(5)))
                .isInstanceOf(PropertyNotFoundException.class);
        assertThatThrownBy(() -> resolver.setValue(context, values, Integer.valueOf(5), "missing"))
                .isInstanceOf(PropertyNotFoundException.class);

        ListELResolver readOnlyResolver = new ListELResolver(true);
        TestELContext readOnlyContext = new TestELContext(readOnlyResolver);
        assertThatThrownBy(() -> readOnlyResolver.setValue(readOnlyContext, values, Integer.valueOf(0), "blocked"))
                .isInstanceOf(PropertyNotWritableException.class);

        List<String> unmodifiableValues = Collections.unmodifiableList(values);
        TestELContext unmodifiableContext = new TestELContext(resolver);
        assertThatThrownBy(() -> resolver.setValue(
                unmodifiableContext, unmodifiableValues, Integer.valueOf(0), "blocked"))
                .isInstanceOf(PropertyNotWritableException.class);
    }

    @Test
    void returnsNullForUnsupportedBasesAndFeatureDescriptors() {
        ListELResolver resolver = new ListELResolver();
        TestELContext context = new TestELContext(resolver);

        assertThat(resolver.getValue(context, null, Integer.valueOf(0))).isNull();
        assertThat(context.isPropertyResolved()).isFalse();
        assertThat(resolver.getType(context, "not a list", Integer.valueOf(0))).isNull();

        Iterator<FeatureDescriptor> descriptors = resolver.getFeatureDescriptors(context, Arrays.asList("value"));
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
