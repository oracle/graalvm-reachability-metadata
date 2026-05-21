/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.utils.uri.FluentPropertyBeanIntrospectorWithIgnores;
import org.apache.commons.beanutils.IntrospectionContext;
import org.junit.jupiter.api.Test;

public class FluentPropertyBeanIntrospectorWithIgnoresTest {
    @Test
    public void introspectsFluentSettersAndSkipsConfiguredIgnores() throws Exception {
        FluentPropertyBeanIntrospectorWithIgnores.addIgnore(ConfigBean.class.getName(), "setIgnored");
        FluentPropertyBeanIntrospectorWithIgnores introspector = new FluentPropertyBeanIntrospectorWithIgnores();
        RecordingIntrospectionContext context = new RecordingIntrospectionContext(ConfigBean.class);

        introspector.introspect(context);

        assertThat(context.propertyNames()).contains("enabled").doesNotContain("ignored");
        assertThat(context.getPropertyDescriptor("enabled")).isNotNull();
    }

    public static final class ConfigBean {
        private boolean enabled;
        private String ignored;

        public ConfigBean setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public ConfigBean setIgnored(String ignored) {
            this.ignored = ignored;
            return this;
        }

        public String getIgnored() {
            return ignored;
        }
    }

    private static final class RecordingIntrospectionContext implements IntrospectionContext {
        private final Class<?> targetClass;
        private final Map<String, PropertyDescriptor> descriptors = new LinkedHashMap<>();

        private RecordingIntrospectionContext(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public void addPropertyDescriptor(PropertyDescriptor descriptor) {
            descriptors.put(descriptor.getName(), descriptor);
        }

        @Override
        public void addPropertyDescriptors(PropertyDescriptor[] descriptors) {
            for (PropertyDescriptor descriptor : descriptors) {
                addPropertyDescriptor(descriptor);
            }
        }

        @Override
        public PropertyDescriptor getPropertyDescriptor(String name) {
            return descriptors.get(name);
        }

        @Override
        public Class<?> getTargetClass() {
            return targetClass;
        }

        @Override
        public boolean hasProperty(String name) {
            return descriptors.containsKey(name);
        }

        @Override
        public Set<String> propertyNames() {
            return descriptors.keySet();
        }

        @Override
        public void removePropertyDescriptor(String name) {
            descriptors.remove(name);
        }
    }
}
