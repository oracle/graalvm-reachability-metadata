/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Binding;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvokerHelperTest {
    @Test
    void constructsAndCallsNamedTypes() throws Exception {
        assertEquals(42, InvokerHelper.invokeStaticMethod("java.lang.Integer", "parseInt", new Object[]{"42"}));
        assertEquals("notes", InvokerHelper.invokeConstructorOf("java.lang.String", new Object[]{"notes"}));
    }

    @Test
    void instantiatesScriptsWithBindingAndDefaultConstructors() throws Exception {
        Binding binding = new Binding(Map.of("message", "hello"));
        assertEquals("hello", InvokerHelper.newScript(BoundScript.class, binding).run());
        assertEquals("hello", InvokerHelper.newScript(DefaultScript.class, binding).run());
        assertEquals("hello", InvokerHelper.createScript(BoundProgram.class, binding).run());
    }

    public static class BoundScript extends Script {
        public BoundScript(Binding binding) {
            super(binding);
        }

        @Override
        public Object run() {
            return getBinding().getVariable("message");
        }
    }

    public static class DefaultScript extends Script {
        @Override
        public Object run() {
            return getBinding().getVariable("message");
        }
    }

    public static class BoundProgram {
        private final Binding binding;

        public BoundProgram(Binding binding) {
            this.binding = binding;
        }

        public Object run() {
            return binding.getVariable("message");
        }
    }
}
