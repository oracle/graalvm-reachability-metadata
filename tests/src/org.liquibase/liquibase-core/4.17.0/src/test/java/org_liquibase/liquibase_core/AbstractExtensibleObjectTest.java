/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.AbstractExtensibleObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractExtensibleObjectTest {

    @Test
    void discoversReadsAndWritesPublicAttributeFields() {
        ExtensibleFixture fixture = new ExtensibleFixture();

        fixture.set("name", "customer");
        fixture.set("quantity", "17");
        fixture.set("dynamicAttribute", "dynamic value");

        assertThat(fixture.getAttributes()).contains("name", "quantity", "dynamicAttribute");
        assertThat(fixture.get("name", String.class)).isEqualTo("customer");
        assertThat(fixture.get("quantity", Integer.class)).isEqualTo(17);
        assertThat(fixture.get("dynamicAttribute", String.class)).isEqualTo("dynamic value");
    }

    @Test
    void cloneCopiesPublicCollectionMapAndNestedExtensibleObjectFields() {
        ExtensibleFixture fixture = new ExtensibleFixture();
        fixture.set("name", "inventory");
        fixture.tags.add("green");
        fixture.tags.add("blue");
        fixture.counts.put("available", 12);
        fixture.counts.put("reserved", 3);
        fixture.child.set("name", "child-name");

        ExtensibleFixture clone = (ExtensibleFixture) fixture.clone();

        assertThat(clone).isNotSameAs(fixture);
        assertThat(clone.get("name", String.class)).isEqualTo("inventory");
        assertThat(clone.tags).containsExactly("green", "blue");
        assertThat(clone.tags).isNotSameAs(fixture.tags);
        assertThat(clone.counts).containsExactlyEntriesOf(fixture.counts);
        assertThat(clone.counts).isNotSameAs(fixture.counts);
        assertThat(clone.child).isNotSameAs(fixture.child);
        assertThat(clone.child.get("name", String.class)).isEqualTo("child-name");
    }

    public static class ExtensibleFixture extends AbstractExtensibleObject {
        public String name;
        public Integer quantity;
        public List<String> tags = new ArrayList<>();
        public Map<String, Integer> counts = new LinkedHashMap<>();
        public ChildFixture child = new ChildFixture();
    }

    public static class ChildFixture extends AbstractExtensibleObject {
        public String name;
    }
}
