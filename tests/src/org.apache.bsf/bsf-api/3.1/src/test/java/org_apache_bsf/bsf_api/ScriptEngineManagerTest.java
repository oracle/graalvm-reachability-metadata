/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bsf.bsf_api;

import javax.script.Bindings;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ScriptEngineManagerTest {
    @Test
    void storesAndReplacesGlobalBindings() {
        ScriptEngineManager manager = new ScriptEngineManager();
        manager.put("customer", "Ada");
        assertEquals("Ada", manager.get("customer"));
        assertEquals("Ada", manager.getBindings().get("customer"));
        manager.getBindings().put("quantity", 3);
        assertEquals(3, manager.get("quantity"));

        Bindings replacements = new SimpleBindings();
        replacements.put("currency", "EUR");
        manager.setBindings(replacements);
        assertSame(replacements, manager.getBindings());
        assertNull(manager.get("customer"));
        manager.put("quantity", 4);
        assertEquals(4, replacements.get("quantity"));
        assertEquals("EUR", manager.get("currency"));
        assertEquals("EUR", manager.getBindings().remove("currency"));
        assertFalse(manager.getBindings().containsKey("currency"));
    }

    @Test
    void returnsNullForUnknownEngineIdentifiers() {
        ScriptEngineManager manager = new ScriptEngineManager();
        assertNull(manager.getEngineByName("no-such-bsf-test-language"));
        assertNull(manager.getEngineByExtension("no-such-bsf-test-extension"));
        assertNull(manager.getEngineByMimeType("application/x-no-such-bsf-test-language"));
    }

    @Test
    void rejectsInvalidBindingsAndLookupArguments() {
        ScriptEngineManager manager = new ScriptEngineManager();
        assertThrows(IllegalArgumentException.class, () -> manager.setBindings(null));
        assertThrows(NullPointerException.class, () -> manager.put(null, 1));
        assertThrows(IllegalArgumentException.class, () -> manager.put("", 1));
        assertThrows(NullPointerException.class, () -> manager.getEngineByName(null));
        assertThrows(NullPointerException.class, () -> manager.getEngineByExtension(null));
        assertThrows(NullPointerException.class, () -> manager.getEngineByMimeType(null));
        assertThrows(NullPointerException.class, () -> manager.registerEngineName("name", null));
        assertThrows(NullPointerException.class, () -> manager.registerEngineExtension("ext", null));
        assertThrows(NullPointerException.class, () -> manager.registerEngineMimeType("application/x-test", null));
    }
}
