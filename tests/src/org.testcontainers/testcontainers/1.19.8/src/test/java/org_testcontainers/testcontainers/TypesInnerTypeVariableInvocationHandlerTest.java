/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.reflect.TypeParameter;
import org.testcontainers.shaded.com.google.common.reflect.TypeToken;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    void substitutesATypeVariableThroughThePublicTypeTokenApi() {
        TypeToken<List<String>> token = listOf(String.class);

        assertThat(token.toString()).contains("java.lang.String");
        assertThat(token.getRawType()).isEqualTo(List.class);

        TypeToken<? extends List<? extends Number>> captured = new TypeToken<List<? extends Number>>() {}
            .getSubtype(ArrayList.class);
        assertThat(captured.toString()).contains("java.util.ArrayList");
    }

    private static <T> TypeToken<List<T>> listOf(Class<T> elementType) {
        return new TypeToken<List<T>>() {}.where(new TypeParameter<T>() {}, elementType);
    }
}
