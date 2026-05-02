/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.internal.$Gson$Types;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GsonInnerTypesTest {
    @Test
    public void resolvesRawClassForGenericArrayType() {
        final GenericArrayType genericArrayType = $Gson$Types.arrayOf(String.class);

        final Class<?> rawType = $Gson$Types.getRawType(genericArrayType);

        assertThat(rawType).isEqualTo(String[].class);
    }

    @Test
    public void resolvesRawClassForParameterizedGenericArrayType() {
        final ParameterizedType listOfStringType = $Gson$Types.newParameterizedTypeWithOwner(
                null, List.class, String.class);
        final Type genericListArrayType = $Gson$Types.arrayOf(listOfStringType);

        final Class<?> rawType = $Gson$Types.getRawType(genericListArrayType);

        assertThat(rawType).isEqualTo(List[].class);
    }
}
