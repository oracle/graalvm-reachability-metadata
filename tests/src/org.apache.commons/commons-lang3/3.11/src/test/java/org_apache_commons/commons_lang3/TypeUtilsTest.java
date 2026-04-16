/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.jupiter.api.Test;

public class TypeUtilsTest {

    @Test
    void resolvesRawTypesForGenericArrayTypes() {
        final GenericArrayType stringArrayType = TypeUtils.genericArrayType(String.class);
        final Type listOfStringType = TypeUtils.parameterize(List.class, String.class);
        final GenericArrayType listArrayType = TypeUtils.genericArrayType(listOfStringType);

        assertThat(TypeUtils.getRawType(stringArrayType, null)).isEqualTo(String[].class);
        assertThat(TypeUtils.getRawType(listArrayType, null)).isEqualTo(List[].class);
    }
}
