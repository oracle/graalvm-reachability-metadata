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
    public void resolvesArrayPropertyTypeFromItsComponentClass() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass targetClass = reflectionManager.toXClass(ArrayProperties.class);
        XProperty namesProperty = declaredFieldProperty(targetClass, "names");

        assertThat(namesProperty.isArray()).isTrue();
        assertThat(reflectionManager.toClass(namesProperty.getElementClass())).isEqualTo(String.class);
        assertThat(reflectionManager.toClass(namesProperty.getClassOrElementClass())).isEqualTo(String.class);
        assertThat(reflectionManager.toClass(namesProperty.getType())).isEqualTo(String[].class);
    }

    private static XProperty declaredFieldProperty(XClass targetClass, String name) {
        List<XProperty> properties = targetClass.getDeclaredProperties(XClass.ACCESS_FIELD);
        for (XProperty property : properties) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        throw new AssertionError("Missing field property: " + name);
    }

    public static class ArrayProperties {
        public String[] names = { "first", "second" };
    }
}
