/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.velocity.util.introspection.IntrospectionUtils;
import org.junit.jupiter.api.Test;

public class IntrospectionUtilsTest {
    @Test
    void resolvesGenericArrayTypeToArrayClass() {
        Type genericListType = TypeUtils.parameterize(List.class, String.class);
        Type genericArrayType = TypeUtils.genericArrayType(genericListType);

        Class<?> resolvedClass = IntrospectionUtils.getTypeClass(genericArrayType);

        assertThat(resolvedClass).isEqualTo(List[].class);
    }
}
