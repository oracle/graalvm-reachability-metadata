/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.bind.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("rawtypes")
public class ReflectionNavigatorTest {
    @Test
    void recognizesPublicAndPrivateDefaultConstructors() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();

        assertThat(navigator.hasDefaultConstructor(ObjectFactory.class)).isTrue();
        assertThat(navigator.hasDefaultConstructor(PrivateConstructor.class)).isTrue();
        assertThat(navigator.hasDefaultConstructor(RequiredArgument.class)).isFalse();
    }

    @Test
    void describesEnumConstantsInDeclarationOrder() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();

        assertThat(navigator.getEnumConstants(Priority.class))
                .extracting(navigator::getFieldName).containsExactly("NORMAL", "URGENT");
    }

    @Test
    void locatesTheObjectFactoryForAModelPackage() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();

        assertThat(navigator.loadObjectFactory(ReflectionNavigatorTest.class,
                "com_sun_xml_bind.jaxb_core")).isEqualTo(ObjectFactory.class);
    }

    @Test
    void preservesPrimitiveArrayFieldTypes() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();
        Field field = navigator.getDeclaredField(ArrayModel.class, "counts");
        Type type = navigator.getFieldType(field);

        assertThat(type).isEqualTo(int[].class);
        assertThat(navigator.isArray(type)).isTrue();
        assertThat(navigator.getComponentType(type)).isEqualTo(int.class);
    }

    public enum Priority {
        NORMAL, URGENT
    }

    public static class PrivateConstructor {
        private PrivateConstructor() {
        }
    }

    public static class RequiredArgument {
        public RequiredArgument(String name) {
        }
    }

    public static class ArrayModel {
        public int[] counts;
    }
}
