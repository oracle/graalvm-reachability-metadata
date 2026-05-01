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
import javax.el.MapELResolver;
import javax.el.PropertyNotWritableException;
import javax.el.VariableMapper;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MapELResolverTest {
    @Test
    void reportsObjectTypeForMapValuesAndKeys() {
        MapELResolver resolver = new MapELResolver();
        TestELContext context = new TestELContext(resolver);
        Map<String, String> values = new HashMap<>();
        values.put("answer", "forty two");

        Class<?> valueType = resolver.getType(context, values, "answer");
        Class<?> commonPropertyType = resolver.getCommonPropertyType(context, values);

        assertThat(valueType).isSameAs(Object.class);
        assertThat(context.isPropertyResolved()).isTrue();
        assertThat(commonPropertyType).isSameAs(Object.class);
    }

    @Test
    void readsAndWritesMapEntriesUsingPropertiesAsKeys() {
        MapELResolver resolver = new MapELResolver();
        TestELContext context = new TestELContext(resolver);
        Map<Object, String> values = new LinkedHashMap<>();
        values.put("name", "initial");
        values.put(Integer.valueOf(7), "seven");

        Object namedValue = resolver.getValue(context, values, "name");

        assertThat(namedValue).isEqualTo("initial");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        Object numericKeyValue = resolver.getValue(context, values, Integer.valueOf(7));

        assertThat(numericKeyValue).isEqualTo("seven");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, values, "name", "updated");

        assertThat(values).containsEntry("name", "updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void reportsReadOnlyStateForMutableConfiguredAndUnmodifiableMaps() {
        Map<String, String> mutableValues = new HashMap<>();
        mutableValues.put("name", "value");
        MapELResolver resolver = new MapELResolver();
        TestELContext context = new TestELContext(resolver);

        assertThat(resolver.isReadOnly(context, mutableValues, "name")).isFalse();
        assertThat(context.isPropertyResolved()).isTrue();

        MapELResolver configuredReadOnlyResolver = new MapELResolver(true);
        TestELContext configuredReadOnlyContext = new TestELContext(configuredReadOnlyResolver);
        assertThat(configuredReadOnlyResolver.isReadOnly(configuredReadOnlyContext, mutableValues, "name")).isTrue();
        assertThat(configuredReadOnlyContext.isPropertyResolved()).isTrue();

        Map<String, String> unmodifiableValues = Collections.unmodifiableMap(new HashMap<>(mutableValues));
        TestELContext unmodifiableContext = new TestELContext(resolver);
        assertThat(resolver.isReadOnly(unmodifiableContext, unmodifiableValues, "name")).isTrue();
        assertThat(unmodifiableContext.isPropertyResolved()).isTrue();
    }

    @Test
    void rejectsWritesToConfiguredAndUnmodifiableReadOnlyMaps() {
        Map<String, String> mutableValues = new HashMap<>();
        mutableValues.put("name", "value");
        MapELResolver readOnlyResolver = new MapELResolver(true);
        TestELContext readOnlyContext = new TestELContext(readOnlyResolver);

        assertThatThrownBy(() -> readOnlyResolver.setValue(readOnlyContext, mutableValues, "name", "blocked"))
                .isInstanceOf(PropertyNotWritableException.class);
        assertThat(readOnlyContext.isPropertyResolved()).isTrue();

        MapELResolver resolver = new MapELResolver();
        Map<String, String> unmodifiableValues = Collections.unmodifiableMap(new HashMap<>(mutableValues));
        TestELContext unmodifiableContext = new TestELContext(resolver);
        assertThatThrownBy(() -> resolver.setValue(unmodifiableContext, unmodifiableValues, "name", "blocked"))
                .isInstanceOf(PropertyNotWritableException.class);
        assertThat(unmodifiableContext.isPropertyResolved()).isTrue();
    }

    @Test
    void describesMapKeysAsFeatureDescriptors() {
        MapELResolver resolver = new MapELResolver();
        TestELContext context = new TestELContext(resolver);
        Map<Object, String> values = new LinkedHashMap<>();
        values.put("name", "value");
        values.put(Integer.valueOf(7), "seven");

        Iterator<FeatureDescriptor> descriptors = resolver.getFeatureDescriptors(context, values);
        List<FeatureDescriptor> descriptorList = new ArrayList<>();
        while (descriptors.hasNext()) {
            descriptorList.add(descriptors.next());
        }

        FeatureDescriptor nameDescriptor = descriptorNamed(descriptorList, "name");
        assertThat(nameDescriptor.getDisplayName()).isEqualTo("name");
        assertThat(nameDescriptor.getShortDescription()).isEmpty();
        assertThat(nameDescriptor.isExpert()).isFalse();
        assertThat(nameDescriptor.isHidden()).isFalse();
        assertThat(nameDescriptor.isPreferred()).isTrue();
        assertThat(nameDescriptor.getValue(ELResolver.TYPE)).isSameAs(String.class);
        assertThat(nameDescriptor.getValue(ELResolver.RESOLVABLE_AT_DESIGN_TIME)).isEqualTo(Boolean.TRUE);

        FeatureDescriptor numericDescriptor = descriptorNamed(descriptorList, "7");
        assertThat(numericDescriptor.getValue(ELResolver.TYPE)).isSameAs(Integer.class);
    }

    @Test
    void returnsNullForUnsupportedBasesWithoutResolvingContext() {
        MapELResolver resolver = new MapELResolver();
        TestELContext context = new TestELContext(resolver);

        assertThat(resolver.getValue(context, null, "name")).isNull();
        assertThat(context.isPropertyResolved()).isFalse();
        assertThat(resolver.getType(context, "not a map", "name")).isNull();
        assertThat(context.isPropertyResolved()).isFalse();
        assertThat(resolver.getCommonPropertyType(context, "not a map")).isNull();
        assertThat(resolver.getFeatureDescriptors(context, "not a map")).isNull();
    }

    private static FeatureDescriptor descriptorNamed(List<FeatureDescriptor> descriptors, String expectedName) {
        for (FeatureDescriptor descriptor : descriptors) {
            if (expectedName.equals(descriptor.getName())) {
                return descriptor;
            }
        }
        throw new AssertionError("Missing descriptor named " + expectedName);
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
