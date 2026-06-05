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
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractExtensibleObjectTest {

    @Test
    void publicFieldsAreExtensibleAttributes() {
        PublicFieldObject object = new PublicFieldObject();

        object.set("name", "updated");
        object.set("priority", 7);

        SortedSet<String> attributes = object.getAttributes();
        assertTrue(attributes.contains("name"));
        assertTrue(attributes.contains("priority"));
        assertEquals("updated", object.get("name", String.class));
        assertEquals(7, object.get("priority", Integer.class));
    }

    @Test
    void cloneCopiesPublicCollectionAndMapFields() {
        PublicFieldObject object = new PublicFieldObject();
        object.items.add("alpha");
        object.items.add("beta");
        object.options.put("mode", "strict");

        PublicFieldObject clone = (PublicFieldObject) object.clone();

        assertEquals(object.items, clone.items);
        assertEquals(object.options, clone.options);
        assertNotSame(object.items, clone.items);
        assertNotSame(object.options, clone.options);
    }

    public static class PublicFieldObject extends AbstractExtensibleObject {
        public String name = "initial";
        public Integer priority;
        public List<String> items = new ArrayList<>();
        public Map<String, String> options = new LinkedHashMap<>();
    }
}
