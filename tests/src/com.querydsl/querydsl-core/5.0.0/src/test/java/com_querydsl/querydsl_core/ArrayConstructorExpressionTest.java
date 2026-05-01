/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.ArrayConstructorExpression;
import com.querydsl.core.types.dsl.Expressions;

import org.junit.jupiter.api.Test;

public class ArrayConstructorExpressionTest {

    @Test
    void newInstanceCopiesVarargsIntoTypedArray() {
        ArrayConstructorExpression<String> expression = new ArrayConstructorExpression<>(
                String[].class,
                Expressions.stringPath("first"),
                Expressions.stringPath("second"));

        String[] values = expression.newInstance("alpha", "beta");

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly("alpha", "beta");
        assertThat(expression.getElementType()).isEqualTo(String.class);
    }
}
