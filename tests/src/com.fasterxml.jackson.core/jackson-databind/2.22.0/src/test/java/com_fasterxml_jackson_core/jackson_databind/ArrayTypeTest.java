/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeTest {

    @Test
    void constructsArrayTypeForComponentClass() {
        ArrayType arrayType = TypeFactory.defaultInstance().constructArrayType(String.class);

        assertThat(arrayType.isArrayType()).isTrue();
        assertThat(arrayType.getRawClass()).isSameAs(String[].class);
        assertThat(arrayType.getContentType().getRawClass()).isSameAs(String.class);
        assertThat(arrayType.getEmptyArray()).isEmpty();
    }

    @Test
    void createsArrayTypeWithReplacementContentType() {
        TypeFactory typeFactory = TypeFactory.defaultInstance();
        ArrayType stringArrayType = typeFactory.constructArrayType(String.class);
        JavaType integerType = typeFactory.constructType(Integer.class);

        JavaType integerArrayType = stringArrayType.withContentType(integerType);

        assertThat(integerArrayType).isInstanceOf(ArrayType.class);
        assertThat(integerArrayType.getRawClass()).isSameAs(Integer[].class);
        assertThat(integerArrayType.getContentType().getRawClass()).isSameAs(Integer.class);
        assertThat(((ArrayType) integerArrayType).getEmptyArray()).isEmpty();
    }
}
