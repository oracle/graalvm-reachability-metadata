/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ScriptTest {
    @Test
    void routesPropertiesToSettersOrTheBinding() {
        NamedScript script = new NamedScript();
        script.setProperty("title", "Ledger");
        script.setProperty("quantity", 6);
        assertEquals("Ledger", script.run());
        assertFalse(script.getBinding().hasVariable("title"));
        assertEquals(6, script.getBinding().getVariable("quantity"));
    }

    public static class NamedScript extends Script {
        private String title;

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public Object run() {
            return title;
        }
    }
}
