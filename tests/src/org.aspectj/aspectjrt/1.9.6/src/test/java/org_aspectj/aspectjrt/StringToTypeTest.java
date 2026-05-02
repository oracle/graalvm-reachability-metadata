/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.aspectj.internal.lang.reflect.StringToType;
import org.aspectj.lang.reflect.AjType;
import org.junit.jupiter.api.Test;

public class StringToTypeTest {
    @Test
    void convertsParameterizedTypeNameUsingScopeClassLoader() throws Exception {
        Type type = StringToType.stringToType("java.util.List<java.lang.String>", GenericScope.class);

        assertThat(type).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) type;
        assertThat(parameterizedType.getRawType()).isEqualTo(List.class);
        assertThat(parameterizedType.getOwnerType()).isNull();
        assertThat(parameterizedType.getActualTypeArguments()).hasSize(1);

        Type typeArgument = parameterizedType.getActualTypeArguments()[0];
        assertThat(typeArgument).isInstanceOf(AjType.class);
        assertThat(((AjType<?>) typeArgument).getJavaClass()).isEqualTo(String.class);
    }

    public static class GenericScope<T> {
    }
}
