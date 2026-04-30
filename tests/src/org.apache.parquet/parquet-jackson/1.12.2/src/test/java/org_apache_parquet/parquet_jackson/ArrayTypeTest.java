/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import org.junit.jupiter.api.Test;
import shaded.parquet.com.fasterxml.jackson.databind.JavaType;
import shaded.parquet.com.fasterxml.jackson.databind.type.ArrayType;
import shaded.parquet.com.fasterxml.jackson.databind.type.TypeFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeTest {
    private final TypeFactory typeFactory = TypeFactory.defaultInstance();

    @Test
    void constructArrayTypeCreatesRuntimeArrayClassForComponentType() {
        ArrayType arrayType = typeFactory.constructArrayType(String.class);

        assertThat(arrayType.isArrayType()).isTrue();
        assertThat(arrayType.isContainerType()).isTrue();
        assertThat(arrayType.getRawClass()).isEqualTo(String[].class);
        assertThat(arrayType.getContentType().getRawClass()).isEqualTo(String.class);
    }

    @Test
    void withContentTypeCreatesNewRuntimeArrayClassForReplacementComponentType() {
        ArrayType stringArrayType = typeFactory.constructArrayType(String.class);
        JavaType integerType = typeFactory.constructType(Integer.class);

        JavaType integerArrayType = stringArrayType.withContentType(integerType);

        assertThat(integerArrayType).isInstanceOf(ArrayType.class);
        assertThat(integerArrayType.isArrayType()).isTrue();
        assertThat(integerArrayType.getRawClass()).isEqualTo(Integer[].class);
        assertThat(integerArrayType.getContentType()).isEqualTo(integerType);
        assertThat(stringArrayType.getRawClass()).isEqualTo(String[].class);
    }
}
