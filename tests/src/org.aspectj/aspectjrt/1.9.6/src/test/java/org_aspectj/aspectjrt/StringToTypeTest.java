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
import java.lang.reflect.TypeVariable;
import java.util.List;

import org.aspectj.internal.lang.reflect.StringToType;
import org.aspectj.lang.reflect.AjType;
import org.junit.jupiter.api.Test;

public class StringToTypeTest {
    @Test
    void convertsParameterizedTypeNameToReflectiveParameterizedType() throws ClassNotFoundException {
        Type type = StringToType.stringToType(
                "java.util.List<java.lang.String>",
                GenericScope.class);

        assertThat(type).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) type;
        assertThat(parameterizedType.getRawType()).isEqualTo(List.class);
        assertThat(parameterizedType.getOwnerType()).isNull();
        assertThat(parameterizedType.getActualTypeArguments())
                .singleElement()
                .isInstanceOfSatisfying(AjType.class, argument ->
                        assertThat(argument.getJavaClass()).isEqualTo(String.class));
    }

    @Test
    void convertsTypeVariableNameFromClassScope() throws ClassNotFoundException {
        Type type = StringToType.stringToType("T", GenericScope.class);

        assertThat(type).isInstanceOf(TypeVariable.class);
        TypeVariable<?> typeVariable = (TypeVariable<?>) type;
        assertThat(typeVariable.getName()).isEqualTo("T");
        assertThat(typeVariable.getGenericDeclaration()).isEqualTo(GenericScope.class);
    }

    public static class GenericScope<T> {
    }
}
