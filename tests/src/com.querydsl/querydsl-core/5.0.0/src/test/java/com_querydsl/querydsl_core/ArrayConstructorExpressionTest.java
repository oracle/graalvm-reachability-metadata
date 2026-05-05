/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.ArrayConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

public class ArrayConstructorExpressionTest {

    @Test
    void newInstanceConvertsObjectVarargsToTypedArray() {
        ArrayConstructorExpression<String> projection = Projections.array(
                String[].class,
                Expressions.constant("first"),
                Expressions.constant("second"));

        String[] values = projection.newInstance("first", "second");

        assertThat(values.getClass()).isEqualTo(String[].class);
        assertThat(values).containsExactly("first", "second");
    }
}
