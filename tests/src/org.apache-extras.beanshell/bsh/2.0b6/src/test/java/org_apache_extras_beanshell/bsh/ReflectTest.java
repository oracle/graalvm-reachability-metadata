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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectTest {

    @BeforeEach
    public void disableAccessibilityBeforeTest() throws Exception {
        Capabilities.setAccessibility(false);
    }

    @AfterEach
    public void disableAccessibilityAfterTest() throws Exception {
        Capabilities.setAccessibility(false);
    }

    @Test
    public void constructorsAndMethodInvocationUsePublicLookupByDefault() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                value = new String("BeanShell");
                return value.toUpperCase();
                """);

        assertThat(result).isEqualTo("BEANSHELL");
    }

    @Test
    public void accessibilityModeUsesDeclaredConstructorsFieldsAndMethods() throws Exception {
        Capabilities.setAccessibility(true);
        Interpreter interpreter = new Interpreter();
        interpreter.set("bean", new FieldAccessBean());

        Object result = interpreter.eval("""
                value = new String("Declared");
                publicValue = bean.publicText;
                privateValue = bean.privateText;
                return publicValue + ":" + privateValue + ":" + value.toLowerCase();
                """);

        assertThat(result).isEqualTo("visible:hidden:declared");
    }

    @Test
    public void objectPropertyAccessUsesGetAndIsAccessors() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.set("bean", new PropertyBean("initial", true));

        Object result = interpreter.eval("""
                title = bean.title;
                enabled = bean.enabled;
                return title + ":" + enabled;
                """);

        assertThat(result).isEqualTo("initial:true");
    }

    @Test
    public void objectPropertyAssignmentFindsSetter() throws Exception {
        Interpreter interpreter = new Interpreter();
        PropertyBean bean = new PropertyBean("initial", false);
        interpreter.set("bean", bean);

        Object result = interpreter.eval("""
                bean.title = "updated";
                return bean.title;
                """);

        assertThat(result).isEqualTo("updated");
        assertThat(bean.getTitle()).isEqualTo("updated");
    }

    public static class FieldAccessBean {
        public String publicText = "visible";
        private String privateText = "hidden";
    }

    public static class PropertyBean {
        private String title;
        private final boolean enabled;

        public PropertyBean(String title, boolean enabled) {
            this.title = title;
            this.enabled = enabled;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
