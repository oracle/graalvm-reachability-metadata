/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.introspector.PropertySubstitute;

public class PropertySubstituteTest {

    @Test
    void usesInheritedPrivateReadAndWriteMethods() throws Exception {
        PropertySubstitute property =
                new PropertySubstitute("value", String.class, "readValue", "writeValue");
        MethodBackedBean bean = new MethodBackedBean();

        property.setTargetType(MethodBackedBean.class);
        property.set(bean, "updated");

        assertThat(property.get(bean)).isEqualTo("updated");
    }

    @Test
    void usesInheritedPrivateFieldWhenNoAccessorMethodsExist() throws Exception {
        PropertySubstitute property = new PropertySubstitute("fieldOnly", String.class);
        FieldBackedBean bean = new FieldBackedBean();

        property.setTargetType(FieldBackedBean.class);
        property.set(bean, "field value");

        assertThat(property.get(bean)).isEqualTo("field value");
    }

    @Test
    void fillsCollectionValuesThroughSingleElementAdder() throws Exception {
        PropertySubstitute property =
                new PropertySubstitute("items", List.class, null, "addItem", String.class);
        FillerBean bean = new FillerBean();

        property.setTargetType(FillerBean.class);
        property.set(bean, List.of("first", "second"));

        assertThat(bean.items).containsExactly("first", "second");
    }

    @Test
    void fillsMapValuesThroughKeyValueAdder() throws Exception {
        PropertySubstitute property = new PropertySubstitute(
                "entries", Map.class, null, "putEntry", String.class, Integer.class);
        FillerBean bean = new FillerBean();
        Map<String, Integer> entries = new LinkedHashMap<>();
        entries.put("left", 1);
        entries.put("right", 2);

        property.setTargetType(FillerBean.class);
        property.set(bean, entries);

        assertThat(bean.entries).containsExactlyEntriesOf(entries);
    }

    @Test
    void fillsArrayValuesThroughSingleElementAdder() throws Exception {
        PropertySubstitute property =
                new PropertySubstitute("items", List.class, null, "addItem", String.class);
        FillerBean bean = new FillerBean();

        property.setTargetType(FillerBean.class);
        property.set(bean, new String[] {"alpha", "beta"});

        assertThat(bean.items).containsExactly("alpha", "beta");
    }

    private static class MethodBackedBase {
        private String value;

        private String readValue() {
            return value;
        }

        private void writeValue(String value) {
            this.value = value;
        }
    }

    private static final class MethodBackedBean extends MethodBackedBase {
    }

    private static class FieldBackedBase {
        private String fieldOnly;
    }

    private static final class FieldBackedBean extends FieldBackedBase {
    }

    private static final class FillerBean {
        private final List<String> items = new ArrayList<>();
        private final Map<String, Integer> entries = new LinkedHashMap<>();

        private void addItem(String item) {
            items.add(item);
        }

        private void putEntry(String key, Integer value) {
            entries.put(key, value);
        }
    }
}
