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

public class ReflectionNavigatorAnonymous10Test {
    private final Navigator<Type, Class<?>, Field, Method> navigator = reflectionNavigator();

    @Test
    void checksOverridingMethodAgainstBaseClassDeclarations() {
        Method overridingMethod = navigator.getDeclaredMethods(OverridingService.class).stream()
                .filter(method -> "describe".equals(navigator.getMethodName(method)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected overriding method to be declared"));

        assertThat(navigator.isOverriding(overridingMethod, BaseService.class)).isTrue();
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

    private static class BaseService {
        String describe(String value) {
            return value;
        }
    }

    private static final class OverridingService extends BaseService {
        @Override
        String describe(String value) {
            return "overridden " + value;
        }
    }
}
