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
import javax.el.PropertyNotWritableException;
import javax.el.ResourceBundleELResolver;
import javax.el.VariableMapper;

import java.beans.FeatureDescriptor;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourceBundleELResolverTest {
    @Test
    void readsValuesFromResourceBundleUsingPropertyText() {
        ResourceBundleELResolver resolver = new ResourceBundleELResolver();
        TestELContext context = new TestELContext(resolver);
        ResourceBundle bundle = bundleWithEntries("greeting", "hello", "7", "seven");

        Object value = resolver.getValue(context, bundle, "greeting");

        assertThat(value).isEqualTo("hello");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        Object coercedValue = resolver.getValue(context, bundle, Integer.valueOf(7));

        assertThat(coercedValue).isEqualTo("seven");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void reportsMissingKeysAndReadOnlyBehavior() {
        ResourceBundleELResolver resolver = new ResourceBundleELResolver();
        TestELContext context = new TestELContext(resolver);
        ResourceBundle bundle = bundleWithEntries("present", "value");

        Object missingValue = resolver.getValue(context, bundle, "missing");

        assertThat(missingValue).isEqualTo("???missing???");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        assertThat(resolver.getType(context, bundle, "present")).isNull();
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        assertThat(resolver.isReadOnly(context, bundle, "present")).isTrue();
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        assertThatThrownBy(() -> resolver.setValue(context, bundle, "present", "changed"))
                .isInstanceOf(PropertyNotWritableException.class)
                .hasMessageContaining("ResourceBundles are immutable");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void describesResourceBundleKeysAsStringFeatureDescriptors() {
        ResourceBundleELResolver resolver = new ResourceBundleELResolver();
        TestELContext context = new TestELContext(resolver);
        ResourceBundle bundle = bundleWithEntries("greeting", "hello", "farewell", "goodbye");

        Iterator<FeatureDescriptor> descriptors = resolver.getFeatureDescriptors(context, bundle);
        FeatureDescriptor greetingDescriptor = null;
        while (descriptors.hasNext()) {
            FeatureDescriptor descriptor = descriptors.next();
            if ("greeting".equals(descriptor.getName())) {
                greetingDescriptor = descriptor;
            }
        }

        assertThat(greetingDescriptor).isNotNull();
        assertThat(greetingDescriptor.getDisplayName()).isEqualTo("greeting");
        assertThat(greetingDescriptor.getName()).isEqualTo("greeting");
        assertThat(greetingDescriptor.isExpert()).isFalse();
        assertThat(greetingDescriptor.isHidden()).isFalse();
        assertThat(greetingDescriptor.isPreferred()).isTrue();
        assertThat(greetingDescriptor.getValue(ELResolver.TYPE)).isSameAs(String.class);
        assertThat(greetingDescriptor.getValue(ELResolver.RESOLVABLE_AT_DESIGN_TIME)).isEqualTo(Boolean.TRUE);
        assertThat(resolver.getCommonPropertyType(context, bundle)).isSameAs(String.class);
    }

    @Test
    void ignoresUnsupportedBasesWithoutResolvingContext() {
        ResourceBundleELResolver resolver = new ResourceBundleELResolver();
        TestELContext context = new TestELContext(resolver);
        Object unsupportedBase = new Object();

        assertThat(resolver.getValue(context, unsupportedBase, "name")).isNull();
        assertThat(context.isPropertyResolved()).isFalse();
        assertThat(resolver.getType(context, unsupportedBase, "name")).isNull();
        assertThat(context.isPropertyResolved()).isFalse();
        assertThat(resolver.isReadOnly(context, unsupportedBase, "name")).isFalse();
        assertThat(context.isPropertyResolved()).isFalse();
        assertThat(resolver.getFeatureDescriptors(context, unsupportedBase)).isNull();
        assertThat(resolver.getCommonPropertyType(context, unsupportedBase)).isNull();
    }

    private static ResourceBundle bundleWithEntries(String firstKey, String firstValue, String... additionalPairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue);
        for (int index = 0; index < additionalPairs.length; index += 2) {
            values.put(additionalPairs[index], additionalPairs[index + 1]);
        }
        return new MapResourceBundle(values);
    }

    private static final class MapResourceBundle extends ResourceBundle {
        private final Map<String, Object> values;

        MapResourceBundle(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        protected Object handleGetObject(String key) {
            return values.get(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(values.keySet());
        }
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
