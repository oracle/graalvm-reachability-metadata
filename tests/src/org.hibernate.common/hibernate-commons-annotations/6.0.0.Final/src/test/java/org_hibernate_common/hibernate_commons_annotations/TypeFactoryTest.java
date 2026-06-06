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

public class TypeFactoryTest {
    @Test
    public void inheritedGenericArrayFieldResolvesToArrayClassForConcreteComponent() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass xClass = reflectionManager.toXClass(StringArrayContainer.class).getSuperclass();
        XProperty values = property(xClass.getDeclaredProperties(XClass.ACCESS_FIELD), "values");

        assertThat(values.isArray()).isTrue();
        assertThat(values.isTypeResolved()).isTrue();
        assertThat(reflectionManager.toClass(values.getElementClass())).isEqualTo(String.class);
        assertThat(reflectionManager.toClass(values.getType())).isEqualTo(String[].class);
    }

    private static XProperty property(List<XProperty> properties, String name) {
        return properties.stream()
                .filter(property -> property.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing property " + name));
    }

    public static class GenericArrayHolder<T> {
        public T[] values;
    }

    public static class StringArrayContainer extends GenericArrayHolder<String> {
    }
}
