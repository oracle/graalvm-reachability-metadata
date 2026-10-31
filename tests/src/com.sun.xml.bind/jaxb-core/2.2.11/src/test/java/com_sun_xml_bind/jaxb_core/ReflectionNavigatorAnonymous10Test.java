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
public class ReflectionNavigatorAnonymous10Test {
    @Test
    void recognizesOverridesInAnAncestorAndDistinguishesNewMethods() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();
        Method label = navigator.getDeclaredMethods(DetailedRecord.class).stream()
                .filter(method -> "label".equals(navigator.getMethodName(method)))
                .findFirst().orElseThrow();
        Method detail = navigator.getDeclaredMethods(DetailedRecord.class).stream()
                .filter(method -> "detail".equals(navigator.getMethodName(method)))
                .findFirst().orElseThrow();

        assertThat(navigator.isOverriding(label, IntermediateRecord.class)).isTrue();
        assertThat(navigator.isOverriding(detail, IntermediateRecord.class)).isFalse();
    }

    public static class BaseRecord {
        public String label() {
            return "record";
        }
    }

    public static class IntermediateRecord extends BaseRecord {
    }

    public static class DetailedRecord extends IntermediateRecord {
        @Override
        public String label() {
            return "detailed record";
        }

        public String detail() {
            return "details";
        }
    }
}
