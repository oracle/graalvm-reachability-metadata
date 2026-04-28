/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.stream.Collectors;

import org.glassfish.jaxb.core.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

public class ReflectionNavigatorAnonymous5Test {
    @Test
    public void listsAllDeclaredMethodsRegardlessOfVisibility() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();

        Collection<? extends Method> methods = navigator.getDeclaredMethods(BeanWithMethods.class);

        assertThat(methods.stream().map(Method::getName).collect(Collectors.toSet()))
                .contains("publicMethod", "privateMethod", "staticMethod");
    }

    @SuppressWarnings("unchecked")
    private static Navigator<Type, Class<?>, Field, Method> navigator() throws Exception {
        Class<?> navigatorType = Class.forName("org.glassfish.jaxb.core.v2.model.nav.ReflectionNavigator");
        Method getInstance = navigatorType.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        return (Navigator<Type, Class<?>, Field, Method>) getInstance.invoke(null);
    }

    private static final class BeanWithMethods {
        public String publicMethod() {
            return "public";
        }

        private int privateMethod() {
            return 1;
        }

        private static boolean staticMethod() {
            return true;
        }
    }
}
