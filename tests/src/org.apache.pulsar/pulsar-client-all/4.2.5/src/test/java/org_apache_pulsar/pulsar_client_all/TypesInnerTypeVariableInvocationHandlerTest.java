/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.apache.pulsar.shade.com.google.common.reflect.TypeResolver;
import org.junit.jupiter.api.Test;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    void resolvesDependentBoundsThroughSyntheticTypeVariable() throws Exception {
        TypeVariable<?> ownerVariable = Owner.class.getTypeParameters()[0];
        Method accept = Owner.class.getDeclaredMethod("accept", Object.class);
        TypeVariable<?> methodVariable = accept.getTypeParameters()[0];
        Type resolved = new TypeResolver()
                .where(ownerVariable, String.class)
                .resolveType(methodVariable);

        assertThat(resolved).isInstanceOf(TypeVariable.class);
        TypeVariable<?> resolvedVariable = (TypeVariable<?>) resolved;
        assertThat(resolvedVariable.getName()).isEqualTo("U");
        assertThat(resolvedVariable.getBounds()).containsExactly(String.class);
    }

    private static final class Owner<T> {
        public <U extends T> void accept(U value) {
        }
    }
}
