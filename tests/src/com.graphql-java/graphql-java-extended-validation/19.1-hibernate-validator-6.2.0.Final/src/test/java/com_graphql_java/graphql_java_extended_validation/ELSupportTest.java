/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java_extended_validation;

import graphql.validation.el.ELSupport;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELSupportTest {

    @Test
    void evaluatesExpressionVariablesWithConfiguredLocale() {
        ELSupport support = new ELSupport(Locale.US);

        Object result = support.evaluate(
                "${formatter.format('%1$.2f', amount)}",
                Map.of("amount", 12.345));

        assertThat(result).isEqualTo("12.35");
    }

    @Test
    void resolvesPublicMethodsForFunctionBinding() throws Throwable {
        ELSupport support = new ELSupport(Locale.US);
        MethodHandle loadMethod = MethodHandles.privateLookupIn(ELSupport.class, MethodHandles.lookup())
                .findVirtual(ELSupport.class,
                        "loadMethod",
                        MethodType.methodType(Method.class, String.class, Class[].class));

        Method method = (Method) loadMethod.invoke(
                support,
                "evaluate",
                new Class<?>[] {String.class, Map.class});

        assertThat(method.getDeclaringClass()).isEqualTo(ELSupport.class);
        assertThat(method.getName()).isEqualTo("evaluate");
        assertThat(method.getParameterTypes()).containsExactly(String.class, Map.class);
    }
}
