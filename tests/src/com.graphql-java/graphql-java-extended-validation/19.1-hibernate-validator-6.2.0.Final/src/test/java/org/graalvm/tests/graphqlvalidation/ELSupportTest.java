/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.tests.graphqlvalidation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import graphql.validation.el.ELSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELSupportTest {

  @Test
  void loadMethodResolvesPublicEvaluationMethod() throws Throwable {
    ELSupport elSupport = new ELSupport(Locale.ENGLISH);
    MethodHandle loadMethod = MethodHandles.privateLookupIn(ELSupport.class, MethodHandles.lookup())
        .findVirtual(ELSupport.class, "loadMethod", MethodType.methodType(Method.class, String.class, Class[].class));

    Method method = (Method) loadMethod.invoke(elSupport, "evaluate", new Class<?>[] {String.class, Map.class});

    assertThat(method.getDeclaringClass()).isEqualTo(ELSupport.class);
    assertThat(method.getName()).isEqualTo("evaluate");
    assertThat(method.getParameterTypes()).containsExactly(String.class, Map.class);
  }
}
