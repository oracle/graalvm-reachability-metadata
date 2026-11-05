/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.codegen_utils;

import com.querydsl.codegen.utils.model.SimpleType;
import com.querydsl.codegen.utils.model.Type;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTypeTest {

    @Test
    void resolvesNamedClassAndCachesIt() {
        SimpleType type = new SimpleType("java.math.BigDecimal", "java.math", "BigDecimal");

        assertThat(type.getJavaClass()).isEqualTo(BigDecimal.class);
        assertThat(type.getJavaClass()).isSameAs(type.getJavaClass());
        assertThat(type.getGenericName(true)).isEqualTo("java.math.BigDecimal");
    }

    @Test
    void resolvesNestedClassFromSourceName() {
        SimpleType type = new SimpleType("java.util.Map.Entry", "java.util", "Entry");

        assertThat(type.getJavaClass()).isEqualTo(Map.Entry.class);
        assertThat(type.isMember()).isTrue();
        assertThat(type.getEnclosingType().getJavaClass()).isEqualTo(Map.class);
    }

    @Test
    void resolvesReferenceArrayDimensionsAndComponent() {
        SimpleType element = new SimpleType("java.math.BigDecimal", "java.math", "BigDecimal");
        Type array = element.asArrayType().asArrayType();

        assertThat(array.getJavaClass()).isEqualTo(BigDecimal[][].class);
        assertThat(array.getComponentType().getJavaClass()).isEqualTo(BigDecimal[].class);
        assertThat(array.getFullName()).isEqualTo("java.math.BigDecimal[][]");
    }

    @Test
    void resolvesPrimitiveArray() {
        SimpleType primitive = new SimpleType("int");

        assertThat(primitive.getJavaClass()).isEqualTo(int.class);
        assertThat(primitive.asArrayType().getJavaClass()).isEqualTo(int[].class);
    }
}
