/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.junit.jupiter.api.Test;

public class JavaXPropertyTest {
    @Test
    public void invokesFieldBackedPropertiesForPrimitiveAndObjectValues() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass targetClass = reflectionManager.toXClass(FieldBackedProperties.class);
        Map<String, XProperty> properties = propertiesByName(targetClass.getDeclaredProperties(XClass.ACCESS_FIELD));
        FieldBackedProperties target = new FieldBackedProperties();

        assertThat(properties.get("booleanValue").invoke(target)).isEqualTo(Boolean.TRUE);
        assertThat(properties.get("byteValue").invoke(target)).isEqualTo(Byte.valueOf((byte) 12));
        assertThat(properties.get("charValue").invoke(target)).isEqualTo(Character.valueOf('h'));
        assertThat(properties.get("intValue").invoke(target)).isEqualTo(Integer.valueOf(42));
        assertThat(properties.get("longValue").invoke(target)).isEqualTo(Long.valueOf(9876543210L));
        assertThat(properties.get("shortValue").invoke(target)).isEqualTo(Short.valueOf((short) 7));
        assertThat(properties.get("textValue").invoke(target)).isEqualTo("hibernate annotations");
    }

    @Test
    public void invokesMethodBackedProperties() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass targetClass = reflectionManager.toXClass(MethodBackedProperties.class);
        Map<String, XProperty> properties = propertiesByName(targetClass.getDeclaredProperties(XClass.ACCESS_PROPERTY));
        MethodBackedProperties target = new MethodBackedProperties("method result", true);

        assertThat(properties.get("description").invoke(target)).isEqualTo("method result");
        assertThat(properties.get("ready").invoke(target)).isEqualTo(Boolean.TRUE);
    }

    private static Map<String, XProperty> propertiesByName(List<XProperty> properties) {
        Map<String, XProperty> result = new HashMap<>();
        for (XProperty property : properties) {
            result.put(property.getName(), property);
        }
        return result;
    }

    public static class FieldBackedProperties {
        public boolean booleanValue = true;
        public byte byteValue = 12;
        public char charValue = 'h';
        public int intValue = 42;
        public long longValue = 9876543210L;
        public short shortValue = 7;
        public String textValue = "hibernate annotations";
    }

    public static class MethodBackedProperties {
        private final String description;
        private final boolean ready;

        public MethodBackedProperties(String description, boolean ready) {
            this.description = description;
            this.ready = ready;
        }

        public String getDescription() {
            return description;
        }

        public boolean isReady() {
            return ready;
        }
    }
}
