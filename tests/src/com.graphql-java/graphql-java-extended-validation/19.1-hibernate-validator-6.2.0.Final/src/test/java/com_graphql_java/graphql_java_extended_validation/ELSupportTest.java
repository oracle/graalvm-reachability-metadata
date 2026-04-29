/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java_extended_validation;

import graphql.validation.el.ELSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ELSupportTest {
    @Test
    void bindsExistingPublicMethodForExpressionLanguageFunctions() throws Throwable {
        ELSupport elSupport = new ELSupport(Locale.ENGLISH);

        assertThatCode(() -> bindPublicMethod(elSupport, "toString")).doesNotThrowAnyException();

        assertThat(elSupport.evaluateBoolean("${candidate == 'accepted'}", Map.of("candidate", "accepted"))).isTrue();
    }

    private static void bindPublicMethod(ELSupport elSupport, String methodName, Class<?>... parameterTypes) throws Throwable {
        MethodHandle bindMethod = MethodHandles.privateLookupIn(ELSupport.class, MethodHandles.lookup())
                .findVirtual(
                        ELSupport.class,
                        "bindMethod",
                        MethodType.methodType(void.class, String.class, String.class, Class[].class));

        bindMethod.invoke(elSupport, "coveredFunction", methodName, parameterTypes);
    }
}
