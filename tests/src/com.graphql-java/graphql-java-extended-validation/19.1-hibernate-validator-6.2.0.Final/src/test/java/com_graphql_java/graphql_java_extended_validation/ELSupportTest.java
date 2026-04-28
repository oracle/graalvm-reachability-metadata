/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java_extended_validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import graphql.validation.el.ELSupport;
import org.junit.jupiter.api.Test;

public class ELSupportTest {
    @Test
    public void loadMethodResolvesPublicMethodsForExpressionSupport() throws Throwable {
        ELSupport elSupport = new ELSupport(Locale.US);

        Method method = loadMethod(elSupport, "evaluate", String.class, Map.class);

        assertThat(method.getDeclaringClass()).isEqualTo(ELSupport.class);
        assertThat(method.getName()).isEqualTo("evaluate");
        assertThat(method.getParameterTypes()).containsExactly(String.class, Map.class);
    }

    private Method loadMethod(ELSupport elSupport, String name, Class<?>... parameterTypes) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ELSupport.class, MethodHandles.lookup());
        MethodHandle loadMethod = lookup.findVirtual(
            ELSupport.class,
            "loadMethod",
            MethodType.methodType(Method.class, String.class, Class[].class)
        );
        return (Method) loadMethod.invoke(elSupport, name, parameterTypes);
    }
}
