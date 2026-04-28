/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.ArrayConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConstructorExpressionTest {
    @Test
    void newInstanceCopiesObjectVarargsIntoTypedArray() {
        ArrayConstructorExpression<String> projection = stringArrayProjection();

        String[] names = projection.newInstance("Ada", "Grace");

        assertThat(names)
                .isInstanceOf(String[].class)
                .containsExactly("Ada", "Grace");
        assertThat(names.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void newInstanceKeepsAlreadyTypedInputArray() {
        ArrayConstructorExpression<String> projection = stringArrayProjection();
        String[] input = {"Katherine", "Dorothy"};

        String[] names = projection.newInstance(input);

        assertThat(names).isSameAs(input);
    }

    private static ArrayConstructorExpression<String> stringArrayProjection() {
        return Projections.array(
                String[].class,
                Expressions.stringPath("firstName"),
                Expressions.stringPath("lastName"));
    }
}
