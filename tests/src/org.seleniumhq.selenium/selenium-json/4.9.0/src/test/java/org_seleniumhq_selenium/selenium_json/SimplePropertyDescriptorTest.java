/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_json;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.SimplePropertyDescriptor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SimplePropertyDescriptorTest {
    @Test
    public void shouldReadBeanStylePropertiesThroughDescriptors() {
        DescriptorBackedEvent event = new DescriptorBackedEvent("download", true, false);
        Map<String, SimplePropertyDescriptor> descriptors = Arrays.stream(
                SimplePropertyDescriptor.getPropertyDescriptors(DescriptorBackedEvent.class))
            .collect(Collectors.toMap(SimplePropertyDescriptor::getName, descriptor -> descriptor));

        assertThat(read(descriptors, "name", event)).isEqualTo("download");
        assertThat(read(descriptors, "active", event)).isEqualTo(true);
        assertThat(read(descriptors, "errors", event)).isEqualTo(false);
        assertThat(read(descriptors, "class", event)).isEqualTo(DescriptorBackedEvent.class.getName());
        assertThat(descriptors).doesNotContainKey("nameWithSuffix");
    }

    private static Object read(
            Map<String, SimplePropertyDescriptor> descriptors,
            String name,
            Object source) {
        SimplePropertyDescriptor descriptor = descriptors.get(name);
        assertThat(descriptor).isNotNull();

        Function<Object, Object> readMethod = descriptor.getReadMethod();
        assertThat(readMethod).isNotNull();

        return readMethod.apply(source);
    }

    public static class DescriptorBackedEvent {
        private final String name;
        private final boolean active;
        private final boolean errors;

        public DescriptorBackedEvent(String name, boolean active, boolean errors) {
            this.name = name;
            this.active = active;
            this.errors = errors;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public boolean hasErrors() {
            return errors;
        }

        public String getNameWithSuffix(String suffix) {
            return name + suffix;
        }
    }
}
