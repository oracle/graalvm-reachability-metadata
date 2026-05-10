/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.glassfish.jaxb.core.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionNavigatorAnonymous3Test {
    private final Navigator<Type, Class<?>, Field, Method> navigator = reflectionNavigator();

    @Test
    void discoversAllDeclaredFieldsThroughNavigator() {
        assertThat(navigator.getDeclaredFields(DeclaredFieldModel.class))
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("id", "name", "active");
    }

    @SuppressWarnings("unchecked")
    private static Navigator<Type, Class<?>, Field, Method> reflectionNavigator() {
        try {
            Class<?> navigatorClass = Class.forName("org.glassfish.jaxb.core.v2.model.nav.ReflectionNavigator");
            Method getInstance = navigatorClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            return (Navigator<Type, Class<?>, Field, Method>) getInstance.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ReflectionNavigator singleton should be available", e);
        }
    }

    private static final class DeclaredFieldModel {
        private final long id = 7L;
        String name = "jaxb";
        boolean active = true;
    }
}
