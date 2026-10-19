/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.apache.pulsar.shade.com.google.common.reflect.TypeResolver;
import org.junit.jupiter.api.Test;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    void resolvesDependentBoundsThroughSyntheticTypeVariable() {
        TypeVariable<?>[] variables = Dependent.class.getTypeParameters();
        Type resolved = new TypeResolver()
                .where(variables[0], String.class)
                .resolveType(variables[1]);

        assertThat(resolved).isInstanceOf(TypeVariable.class);
        TypeVariable<?> resolvedVariable = (TypeVariable<?>) resolved;
        assertThat(resolvedVariable.getName()).isEqualTo("U");
        assertThat(resolvedVariable.getBounds()).containsExactly(String.class);
    }

    private static final class Dependent<T, U extends T> {
    }
}
