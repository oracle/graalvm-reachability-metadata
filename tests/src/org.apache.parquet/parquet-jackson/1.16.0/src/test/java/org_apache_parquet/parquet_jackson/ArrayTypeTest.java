/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.JavaType;
import shaded.parquet.com.fasterxml.jackson.databind.type.ArrayType;
import shaded.parquet.com.fasterxml.jackson.databind.type.TypeFactory;

public class ArrayTypeTest {
    @Test
    void constructsArrayTypeAndReplacesContentType() {
        final TypeFactory typeFactory = TypeFactory.defaultInstance();

        final ArrayType stringArrayType = typeFactory.constructArrayType(String.class);

        assertThat(stringArrayType.isArrayType()).isTrue();
        assertThat(stringArrayType.getRawClass()).isEqualTo(String[].class);
        assertThat(stringArrayType.getContentType().getRawClass()).isEqualTo(String.class);

        final JavaType integerType = typeFactory.constructType(Integer.class);
        final JavaType integerArrayType = stringArrayType.withContentType(integerType);

        assertThat(integerArrayType).isInstanceOf(ArrayType.class);
        assertThat(integerArrayType.getRawClass()).isEqualTo(Integer[].class);
        assertThat(integerArrayType.getContentType()).isEqualTo(integerType);
    }
}
