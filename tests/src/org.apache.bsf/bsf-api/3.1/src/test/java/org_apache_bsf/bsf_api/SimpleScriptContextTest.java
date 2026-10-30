/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bsf.bsf_api;

import java.io.StringReader;
import java.io.StringWriter;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimpleScriptContextTest {
    @Test
    void resolvesEngineScopeBeforeGlobalScope() {
        ScriptContext context = new SimpleScriptContext();
        Bindings globals = new SimpleBindings();
        globals.put("customer", "global customer");
        globals.put("currency", "EUR");
        context.setBindings(globals, ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("customer", "local customer", ScriptContext.ENGINE_SCOPE);

        assertEquals("local customer", context.getAttribute("customer"));
        assertEquals("global customer", context.getAttribute("customer", ScriptContext.GLOBAL_SCOPE));
        assertEquals(ScriptContext.ENGINE_SCOPE, context.getAttributesScope("customer"));
        assertEquals("EUR", context.getAttribute("currency"));
        assertEquals(ScriptContext.GLOBAL_SCOPE, context.getAttributesScope("currency"));
        assertEquals(-1, context.getAttributesScope("missing"));
        assertNull(context.getAttribute("missing"));

        assertEquals("local customer", context.removeAttribute("customer", ScriptContext.ENGINE_SCOPE));
        assertEquals("global customer", context.getAttribute("customer"));
        assertEquals(ScriptContext.GLOBAL_SCOPE, context.getAttributesScope("customer"));
        assertEquals("global customer", context.removeAttribute("customer", ScriptContext.GLOBAL_SCOPE));
        assertNull(context.getAttribute("customer"));
    }

    @Test
    void replacesScopeBindingsAndAllowsAnAbsentGlobalScope() {
        ScriptContext context = new SimpleScriptContext();
        assertNotNull(context.getBindings(ScriptContext.ENGINE_SCOPE));
        assertNull(context.getBindings(ScriptContext.GLOBAL_SCOPE));
        context.setAttribute("ignored", "value", ScriptContext.GLOBAL_SCOPE);
        assertNull(context.getAttribute("ignored", ScriptContext.GLOBAL_SCOPE));
        assertNull(context.removeAttribute("ignored", ScriptContext.GLOBAL_SCOPE));

        Bindings locals = new SimpleBindings();
        context.setBindings(locals, ScriptContext.ENGINE_SCOPE);
        context.setAttribute("count", 7, ScriptContext.ENGINE_SCOPE);
        assertSame(locals, context.getBindings(ScriptContext.ENGINE_SCOPE));
        assertEquals(7, locals.get("count"));

        Bindings globals = new SimpleBindings();
        context.setBindings(globals, ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("shared", "value", ScriptContext.GLOBAL_SCOPE);
        assertSame(globals, context.getBindings(ScriptContext.GLOBAL_SCOPE));
        assertEquals("value", globals.get("shared"));
        context.setBindings(null, ScriptContext.GLOBAL_SCOPE);
        assertNull(context.getAttribute("shared"));
    }

    @Test
    void exposesScopesInSearchOrderAsAnImmutableList() {
        ScriptContext context = new SimpleScriptContext();
        assertEquals(2, context.getScopes().size());
        assertEquals(ScriptContext.ENGINE_SCOPE, context.getScopes().get(0));
        assertEquals(ScriptContext.GLOBAL_SCOPE, context.getScopes().get(1));
        assertThrows(UnsupportedOperationException.class, () -> context.getScopes().clear());
    }

    @Test
    void routesInputOutputAndErrorsToConfiguredStreams() throws Exception {
        ScriptContext context = new SimpleScriptContext();
        assertNotNull(context.getReader());
        assertNotNull(context.getWriter());
        assertNotNull(context.getErrorWriter());
        try (StringReader input = new StringReader("request");
                StringWriter output = new StringWriter();
                StringWriter errors = new StringWriter()) {
            context.setReader(input);
            context.setWriter(output);
            context.setErrorWriter(errors);

            assertSame(input, context.getReader());
            assertSame(output, context.getWriter());
            assertSame(errors, context.getErrorWriter());
            assertEquals('r', context.getReader().read());
            context.getWriter().write("response");
            context.getErrorWriter().write("diagnostic");
            assertEquals("response", output.toString());
            assertEquals("diagnostic", errors.toString());
        }
    }

    @Test
    void rejectsInvalidScopesAndAttributeNames() {
        ScriptContext context = new SimpleScriptContext();
        assertThrows(IllegalArgumentException.class, () -> context.getBindings(-1));
        assertThrows(IllegalArgumentException.class, () -> context.setBindings(new SimpleBindings(), -1));
        assertThrows(IllegalArgumentException.class, () -> context.getAttribute("name", -1));
        assertThrows(IllegalArgumentException.class, () -> context.setAttribute("name", "value", -1));
        assertThrows(IllegalArgumentException.class, () -> context.removeAttribute("name", -1));
        assertThrows(NullPointerException.class, () -> context.setBindings(null, ScriptContext.ENGINE_SCOPE));
        assertThrows(NullPointerException.class, () -> context.getAttribute(null));
        assertThrows(IllegalArgumentException.class, () -> context.getAttribute(""));
        assertThrows(NullPointerException.class, () -> context.setAttribute(null, 1, ScriptContext.ENGINE_SCOPE));
        assertThrows(IllegalArgumentException.class, () -> context.getAttributesScope(""));
        assertThrows(IllegalArgumentException.class, () -> context.removeAttribute("", ScriptContext.ENGINE_SCOPE));
    }
}
