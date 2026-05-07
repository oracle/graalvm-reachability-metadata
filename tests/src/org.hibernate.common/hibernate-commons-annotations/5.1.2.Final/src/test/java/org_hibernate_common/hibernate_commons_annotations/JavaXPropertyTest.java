/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.junit.jupiter.api.Test;

public class JavaXPropertyTest {
    @Test
    public void invokeReadsPrimitiveAndObjectFields() {
        PropertyFixture fixture = new PropertyFixture();
        XClass xClass = new JavaReflectionManager().toXClass(PropertyFixture.class);
        List<XProperty> properties = xClass.getDeclaredProperties(XClass.ACCESS_FIELD);

        assertThat(property(properties, "active").invoke(fixture)).isEqualTo(Boolean.TRUE);
        assertThat(property(properties, "smallNumber").invoke(fixture)).isEqualTo(Byte.valueOf((byte) 7));
        assertThat(property(properties, "letter").invoke(fixture)).isEqualTo(Character.valueOf('H'));
        assertThat(property(properties, "count").invoke(fixture)).isEqualTo(Integer.valueOf(42));
        assertThat(property(properties, "identifier").invoke(fixture)).isEqualTo(Long.valueOf(123456789L));
        assertThat(property(properties, "shortNumber").invoke(fixture)).isEqualTo(Short.valueOf((short) 11));
        assertThat(property(properties, "name").invoke(fixture)).isEqualTo("hibernate-commons-annotations");
    }

    @Test
    public void invokeReadsGetterMethod() {
        PropertyFixture fixture = new PropertyFixture();
        XClass xClass = new JavaReflectionManager().toXClass(PropertyFixture.class);
        List<XProperty> properties = xClass.getDeclaredProperties(XClass.ACCESS_PROPERTY);

        assertThat(property(properties, "summary").invoke(fixture)).isEqualTo("hibernate-commons-annotations:42");
    }

    private static XProperty property(List<XProperty> properties, String name) {
        return properties.stream()
                .filter(property -> property.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing property " + name));
    }

    public static class PropertyFixture {
        public boolean active = true;
        public byte smallNumber = 7;
        public char letter = 'H';
        public int count = 42;
        public long identifier = 123456789L;
        public short shortNumber = 11;
        public String name = "hibernate-commons-annotations";

        public String getSummary() {
            return name + ":" + count;
        }
    }
}
