/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.jupiter.api.Test;

public class TypeUtilsTest {

    @Test
    public void getRawTypeCreatesArrayClassForGenericArrayTypes() {
        Type genericArrayType = TypeUtils.genericArrayType(TypeUtils.parameterize(List.class, String.class));

        Class<?> rawType = TypeUtils.getRawType(genericArrayType, null);

        assertThat(rawType).isEqualTo(List[].class);
        assertThat(rawType.getComponentType()).isEqualTo(List.class);
    }
}
