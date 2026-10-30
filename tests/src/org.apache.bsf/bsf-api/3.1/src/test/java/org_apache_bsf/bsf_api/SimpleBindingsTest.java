/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bsf.bsf_api;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleBindingsTest {
    @Test
    void storesReplacesAndRemovesScriptVariables() {
        Bindings bindings = new SimpleBindings();
        assertTrue(bindings.isEmpty());
        assertNull(bindings.put("answer", 41));
        assertEquals(41, bindings.put("answer", 42));
        bindings.put("optional", null);

        assertEquals(42, bindings.get("answer"));
        assertEquals(2, bindings.size());
        assertTrue(bindings.containsValue(42));
        assertTrue(bindings.containsKey("optional"));
        assertNull(bindings.get("optional"));
        assertEquals(42, bindings.remove("answer"));
        assertFalse(bindings.containsKey("answer"));
        assertNull(bindings.remove("missing"));

        bindings.clear();
        assertTrue(bindings.isEmpty());
    }

    @Test
    void sharesTheSuppliedBackingMap() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("customer", "Ada");
        Bindings bindings = new SimpleBindings(variables);

        assertEquals("Ada", bindings.get("customer"));
        bindings.put("quantity", 3);
        assertEquals(3, variables.get("quantity"));
        variables.put("quantity", 4);
        assertEquals(4, bindings.get("quantity"));
        bindings.remove("customer");
        assertFalse(variables.containsKey("customer"));
    }

    @Test
    void mergesBindingsAndExposesLiveCollectionViews() {
        Bindings bindings = new SimpleBindings();
        bindings.put("quantity", 1);
        bindings.putAll(Map.of("quantity", 2, "label", "parcel"));

        assertEquals(2, bindings.get("quantity"));
        assertTrue(bindings.keySet().containsAll(Map.of("quantity", 2, "label", "parcel").keySet()));
        assertTrue(bindings.values().contains("parcel"));
        assertTrue(bindings.entrySet().contains(Map.entry("quantity", 2)));

        bindings.keySet().remove("label");
        assertFalse(bindings.containsKey("label"));
        bindings.values().remove(2);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void rejectsInvalidVariableNames() {
        Bindings bindings = new SimpleBindings();
        assertThrows(NullPointerException.class, () -> bindings.put(null, 1));
        assertThrows(IllegalArgumentException.class, () -> bindings.put("", 1));
        assertThrows(NullPointerException.class, () -> bindings.get(null));
        assertThrows(IllegalArgumentException.class, () -> bindings.get(""));
        assertThrows(ClassCastException.class, () -> bindings.get(1));
        assertThrows(NullPointerException.class, () -> bindings.containsKey(null));
        assertThrows(IllegalArgumentException.class, () -> bindings.containsKey(""));
        assertThrows(ClassCastException.class, () -> bindings.containsKey(1));
        assertThrows(NullPointerException.class, () -> bindings.remove(null));
        assertThrows(IllegalArgumentException.class, () -> bindings.remove(""));
        assertThrows(ClassCastException.class, () -> bindings.remove(1));
        assertThrows(NullPointerException.class, () -> new SimpleBindings(null));
    }

    @Test
    void rejectsInvalidNamesInBulkUpdates() {
        Bindings bindings = new SimpleBindings();
        assertThrows(IllegalArgumentException.class, () -> bindings.putAll(Map.of("", 1)));
        Map<String, Object> nullKey = new HashMap<>();
        nullKey.put(null, 1);
        assertThrows(NullPointerException.class, () -> bindings.putAll(nullKey));
        assertTrue(bindings.isEmpty());
    }
}
