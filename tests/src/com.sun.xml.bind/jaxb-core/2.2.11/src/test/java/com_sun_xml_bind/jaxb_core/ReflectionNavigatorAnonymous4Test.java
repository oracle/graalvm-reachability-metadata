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
public class ReflectionNavigatorAnonymous4Test {
    @Test
    void resolvesANonPublicFieldByName() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();
        Field field = navigator.getDeclaredField(Account.class, "balance");

        assertThat(field).isNotNull();
        assertThat(navigator.getFieldName(field)).isEqualTo("balance");
        assertThat(navigator.getFieldType(field)).isEqualTo(long.class);
        assertThat(navigator.getDeclaringClassForField(field)).isEqualTo(Account.class);
        assertThat(navigator.isPublicField(field)).isFalse();
        assertThat(navigator.getDeclaredField(Account.class, "absent")).isNull();
    }

    public static class Account {
        private long balance;
    }
}
