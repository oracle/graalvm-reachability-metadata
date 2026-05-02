/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Capabilities;
import bsh.Interpreter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectTest {
    @AfterEach
    void resetBeanShellAccessibility() throws Exception {
        Capabilities.setAccessibility(false);
    }

    @Test
    void constructsObjectsWithPublicAndAccessibleConstructorLookup() throws Exception {
        Interpreter interpreter = new Interpreter();

        Capabilities.setAccessibility(false);
        assertThat(interpreter.eval("new String(\"public constructor path\");"))
                .isEqualTo("public constructor path");

        Capabilities.setAccessibility(true);
        assertThat(interpreter.eval("new String(\"declared constructor path\");"))
                .isEqualTo("declared constructor path");
    }

    @Test
    void readsPublicAndPrivateFieldsThroughBeanShellAccess() throws Exception {
        Interpreter interpreter = new Interpreter();
        ReflectiveTarget target = new ReflectiveTarget("visible", "hidden", true);
        interpreter.set("target", target);

        Capabilities.setAccessibility(false);
        assertThat(interpreter.eval("target.publicLabel;"))
                .isEqualTo("visible");

        Capabilities.setAccessibility(true);
        assertThat(interpreter.eval("target.publicLabel;"))
                .isEqualTo("visible");
        assertThat(interpreter.eval("target.privateLabel;"))
                .isEqualTo("hidden");
    }

    @Test
    void invokesPublicAndPrivateMethodsThroughBeanShellAccess() throws Exception {
        Interpreter interpreter = new Interpreter();
        ReflectiveTarget target = new ReflectiveTarget("visible", "hidden", true);
        interpreter.set("target", target);

        Capabilities.setAccessibility(false);
        assertThat(interpreter.eval("target.describe(\"prefix\", 3);"))
                .isEqualTo("prefix:3:visible");

        Capabilities.setAccessibility(true);
        assertThat(interpreter.eval("target.reveal(\"secret\");"))
                .isEqualTo("secret:hidden");
    }

    @Test
    void readsObjectPropertiesThroughGetterFallbacks() throws Exception {
        Interpreter interpreter = new Interpreter();
        ReflectiveTarget target = new ReflectiveTarget("visible", "hidden", true);
        interpreter.set("target", target);

        Capabilities.setAccessibility(false);
        assertThat(interpreter.eval("target.label;"))
                .isEqualTo("visible");
        assertThat(interpreter.eval("target.ready;"))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void writesObjectPropertyThroughSetterFallback() throws Exception {
        Interpreter interpreter = new Interpreter();
        ReflectiveTarget target = new ReflectiveTarget("visible", "hidden", false);
        interpreter.set("target", target);

        Capabilities.setAccessibility(false);
        interpreter.eval("target.label = \"updated\";");

        assertThat(target.getLabel()).isEqualTo("updated");
    }

    public static class ReflectiveTarget {
        public String publicLabel;
        private final String privateLabel;
        private final boolean ready;

        public ReflectiveTarget(String publicLabel, String privateLabel, boolean ready) {
            this.publicLabel = publicLabel;
            this.privateLabel = privateLabel;
            this.ready = ready;
        }

        public String describe(String prefix, int count) {
            return prefix + ":" + count + ":" + publicLabel;
        }

        public String getLabel() {
            return publicLabel;
        }

        public void setLabel(String label) {
            this.publicLabel = label;
        }

        public boolean isReady() {
            return ready;
        }

        private String reveal(String prefix) {
            return prefix + ":" + privateLabel;
        }
    }
}
