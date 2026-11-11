/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.PropertyEditor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BeanUtilsTest {
    @Test
    @SuppressWarnings("deprecation")
    void instantiatesDefaultPrivateAndRecordConstructors() {
        assertEquals("default", BeanUtils.instantiate(DefaultBean.class).getName());
        assertEquals("default", BeanUtils.instantiateClass(DefaultBean.class).getName());
        assertEquals("default", BeanUtils.instantiateClass(
                BeanUtils.getResolvableConstructor(DefaultBean.class)).getName());
        assertEquals("hidden", BeanUtils.instantiateClass(
                BeanUtils.getResolvableConstructor(HiddenBean.class)).name);
        assertEquals(new Person("Ada", 0), BeanUtils.instantiateClass(
                BeanUtils.findPrimaryConstructor(Person.class), "Ada", null));
    }

    @Test
    void findsPublicAndNonPublicMethods() {
        assertEquals(1, BeanUtils.findMethod(DefaultBean.class, "setName", String.class).getParameterCount());
        assertEquals("secret", BeanUtils.findDeclaredMethod(DefaultBean.class, "secret").getName());
        assertEquals(0, BeanUtils.findMethodWithMinimalParameters(DefaultBean.class, "getName").getParameterCount());
        assertEquals(0, BeanUtils.findDeclaredMethodWithMinimalParameters(
                DefaultBean.class, "secret").getParameterCount());
    }

    @Test
    void copiesBeanPropertiesAndDiscoversConventionEditor() {
        DefaultBean source = new DefaultBean("copied");
        DefaultBean target = new DefaultBean();
        BeanUtils.copyProperties(source, target);
        assertEquals("copied", target.getName());
        PropertyEditor editor = BeanUtils.findEditorByConvention(Label.class);
        assertNotNull(editor);
        editor.setAsText("priority");
        assertEquals(new Label("priority"), editor.getValue());
    }

    public static class DefaultBean {
        private String name;

        public DefaultBean() {
            this("default");
        }

        public DefaultBean(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        private String secret() {
            return this.name;
        }
    }

    public static class HiddenBean {
        private final String name = "hidden";

        private HiddenBean() {
        }
    }

    public record Person(String name, int age) {
    }
}
