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
public class ReflectionNavigatorAnonymous6Test {
    @Test
    void erasesGenericArraysUsingTheirComponentBounds() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();
        Type type = navigator.getFieldType(navigator.getDeclaredField(Measurements.class, "values"));

        assertThat(navigator.isArray(type)).isTrue();
        assertThat(navigator.erasure(type)).isEqualTo(Number[].class);
        assertThat(navigator.erasure(navigator.getComponentType(type))).isEqualTo(Number.class);
    }

    public static class Measurements<T extends Number> {
        public T[] values;
    }
}
