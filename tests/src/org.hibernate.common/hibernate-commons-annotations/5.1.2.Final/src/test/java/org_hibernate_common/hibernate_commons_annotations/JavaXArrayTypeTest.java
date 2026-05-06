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

public class JavaXArrayTypeTest {
    @Test
    public void getTypeResolvesArrayClassFromElementClass() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass xClass = reflectionManager.toXClass(ArrayFixture.class);
        XProperty names = property(xClass.getDeclaredProperties(XClass.ACCESS_FIELD), "names");

        assertThat(names.isArray()).isTrue();
        assertThat(reflectionManager.toClass(names.getElementClass())).isEqualTo(String.class);
        assertThat(reflectionManager.toClass(names.getType())).isEqualTo(String[].class);
    }

    private static XProperty property(List<XProperty> properties, String name) {
        return properties.stream()
                .filter(property -> property.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing property " + name));
    }

    public static class ArrayFixture {
        public String[] names = new String[] {"hibernate", "annotations" };
    }
}
