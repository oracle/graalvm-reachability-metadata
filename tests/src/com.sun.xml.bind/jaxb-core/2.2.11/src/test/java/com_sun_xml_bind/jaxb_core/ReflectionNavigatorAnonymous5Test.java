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
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("rawtypes")
public class ReflectionNavigatorAnonymous5Test {
    @Test
    void describesDeclaredMethodsIncludingNonPublicMethods() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();
        Collection<? extends Method> methods = navigator.getDeclaredMethods(Label.class);

        assertThat(methods).filteredOn(method -> !method.isSynthetic()).extracting(navigator::getMethodName)
                .containsExactlyInAnyOrder("getText", "setText", "normalize");
        Method setter = methods.stream().filter(method -> "setText".equals(navigator.getMethodName(method)))
                .findFirst().orElseThrow();
        assertThat(navigator.getReturnType(setter)).isEqualTo(void.class);
        assertThat(navigator.getMethodParameters(setter)).containsExactly(String.class);
        assertThat(navigator.getDeclaringClassForMethod(setter)).isEqualTo(Label.class);
    }

    public static class Label {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String value) {
            text = normalize(value);
        }

        private String normalize(String value) {
            return value.trim();
        }
    }
}
