/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.apache.hadoop.thirdparty.com.google.common.reflect.TypeResolver;
import org.junit.jupiter.api.Test;

public class TypesInnerTypeVariableInvocationHandlerTest {

    @Test
    void resolvedTypeVariableDelegatesTypeVariableMethodsToArtificialImplementation() throws NoSuchMethodException {
        Method method = BoundedType.class.getDeclaredMethod("method");
        TypeVariable<Method> variable = method.getTypeParameters()[0];
        TypeVariable<Class<BoundedType>> bound = BoundedType.class.getTypeParameters()[0];

        Type resolved = new TypeResolver().where(bound, String.class).resolveType(variable);

        assertThat(resolved).isInstanceOf(TypeVariable.class);
        TypeVariable<?> resolvedVariable = (TypeVariable<?>) resolved;
        assertThat(resolvedVariable.getName()).isEqualTo("T");
        assertThat(resolvedVariable.getGenericDeclaration()).isEqualTo(method);
        assertThat(resolvedVariable.getBounds()).containsExactly(String.class);
        assertThat(resolvedVariable.toString()).isEqualTo("T");
    }

    private static final class BoundedType<U> {
        <T extends U> void method() {
        }
    }
}
