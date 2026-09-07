/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.TypeDescription;
import org.testcontainers.shaded.org.yaml.snakeyaml.constructor.Constructor;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertySubstituteTest {
    @Test
    void usesConfiguredAccessorsForASubstitutedProperty() {
        TypeDescription description = new TypeDescription(Bean.class);
        description.substituteProperty("message", String.class, "readMessage", "assignMessage");
        Yaml yaml = new Yaml(new Constructor(description));

        Bean bean = yaml.load("message: substituted");

        assertThat(bean.readMessage()).isEqualTo("substituted");
        assertThat(description.getProperty("message").get(bean)).isEqualTo("substituted");
    }

    @Test
    void usesFieldsWhenNoAccessorNamesAreConfigured() {
        TypeDescription description = new TypeDescription(FieldBean.class);
        description.substituteProperty("message", String.class, null, null);
        Yaml yaml = new Yaml(new Constructor(description));

        FieldBean bean = yaml.load("message: field-value");

        assertThat(description.getProperty("message").get(bean)).isEqualTo("field-value");
    }

    @Test
    void fillsCollectionMapAndArrayPropertiesThroughAdderMethods() {
        TypeDescription description = new TypeDescription(FillerBean.class);
        description.substituteProperty("items", List.class, null, "addItem", String.class);
        description.substituteProperty("counts", Map.class, null, "putCount", String.class, Integer.class);
        description.substituteProperty("aliases", String[].class, null, "addAlias", String.class);
        Yaml yaml = new Yaml(new Constructor(description));

        FillerBean bean = yaml.load(
            """
            items: [first, second]
            counts: {one: 1, two: 2}
            aliases: [primary, secondary]
            """
        );

        assertThat(bean.items).containsExactly("first", "second");
        assertThat(bean.counts).containsAllEntriesOf(Map.of("one", 1, "two", 2));
        assertThat(bean.aliases).containsExactly("primary", "secondary");
    }

    public static class Bean {
        private String message;

        public String readMessage() {
            return message;
        }

        public void assignMessage(String message) {
            this.message = message;
        }
    }

    public static class FieldBean {
        private String message;
    }

    public static class FillerBean {
        private final List<String> items = new ArrayList<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final List<String> aliases = new ArrayList<>();

        public void addItem(String item) {
            items.add(item);
        }

        public void putCount(String name, Integer count) {
            counts.put(name, count);
        }

        public void addAlias(String alias) {
            aliases.add(alias);
        }
    }
}
