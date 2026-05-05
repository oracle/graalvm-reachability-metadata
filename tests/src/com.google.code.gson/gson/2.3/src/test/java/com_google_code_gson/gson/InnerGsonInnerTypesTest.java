/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.internal.$Gson$Types;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InnerGsonInnerTypesTest {
    @Test
    void resolvesRawClassForGenericArrayType() {
        ParameterizedType listOfStrings = $Gson$Types.newParameterizedTypeWithOwner(
                null,
                List.class,
                String.class);
        GenericArrayType arrayOfLists = $Gson$Types.arrayOf(listOfStrings);

        Class<?> rawType = $Gson$Types.getRawType(arrayOfLists);

        assertThat(rawType).isEqualTo(List[].class);
        assertThat(rawType.isArray()).isTrue();
        assertThat(rawType.getComponentType()).isEqualTo(List.class);
    }
}
