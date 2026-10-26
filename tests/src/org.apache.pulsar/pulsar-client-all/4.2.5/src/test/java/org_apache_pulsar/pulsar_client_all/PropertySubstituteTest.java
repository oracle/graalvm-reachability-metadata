/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.PropertySubstitute;
import org.junit.jupiter.api.Test;

public class PropertySubstituteTest {
    @Test
    void readsAndWritesInheritedMethodAndFieldProperties() throws Exception {
        PropertySubstitute methodProperty = new PropertySubstitute(
                "value", String.class, "readValue", "writeValue");
        MethodBean methodBean = new MethodBean();
        methodProperty.setTargetType(MethodBean.class);
        methodProperty.set(methodBean, "method value");

        PropertySubstitute fieldProperty = new PropertySubstitute("fieldValue", String.class);
        FieldBean fieldBean = new FieldBean();
        fieldProperty.setTargetType(FieldBean.class);
        fieldProperty.set(fieldBean, "field value");

        assertThat(methodProperty.get(methodBean)).isEqualTo("method value");
        assertThat(fieldProperty.get(fieldBean)).isEqualTo("field value");
    }

    @Test
    void fillsCollectionArrayAndMapValuesThroughAdderMethods() throws Exception {
        FillerBean bean = new FillerBean();
        PropertySubstitute listProperty = new PropertySubstitute(
                "items", List.class, null, "addItem", String.class);
        listProperty.setTargetType(FillerBean.class);
        listProperty.set(bean, List.of("one", "two"));
        listProperty.set(bean, new String[] {"three", "four"});

        PropertySubstitute mapProperty = new PropertySubstitute(
                "entries", Map.class, null, "putEntry", String.class, Integer.class);
        mapProperty.setTargetType(FillerBean.class);
        Map<String, Integer> expectedEntries = new LinkedHashMap<>();
        expectedEntries.put("left", 1);
        expectedEntries.put("right", 2);
        mapProperty.set(bean, expectedEntries);

        assertThat(bean.items).containsExactly("one", "two", "three", "four");
        assertThat(bean.entries).containsExactlyEntriesOf(expectedEntries);
    }

    private static class MethodBase {
        private String value;

        private String readValue() {
            return value;
        }

        private void writeValue(String value) {
            this.value = value;
        }
    }

    private static class MethodBean extends MethodBase {
    }

    private static class FieldBase {
        private String fieldValue;
    }

    private static class FieldBean extends FieldBase {
    }

    private static class FillerBean {
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
