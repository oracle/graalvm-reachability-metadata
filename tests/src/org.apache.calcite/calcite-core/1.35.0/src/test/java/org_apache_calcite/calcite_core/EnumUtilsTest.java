/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.enumerable.EnumUtils;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.runtime.SqlFunctions;
import org.apache.calcite.util.Source;
import org.apache.calcite.util.Sources;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumUtilsTest {
    @Test
    public void resolvesExactPublicMethod() {
        MethodCallExpression call = EnumUtils.call(
            null,
            SqlFunctions.class,
            "upper",
            arguments(Expressions.constant("calcite", String.class)));

        assertThat(call.method.getName()).isEqualTo("upper");
        assertThat(call.method.getDeclaringClass()).isEqualTo(SqlFunctions.class);
        assertThat(call.method.getParameterTypes()).containsExactly(String.class);
        assertThat(call.getType()).isEqualTo(String.class);
    }

    @Test
    public void resolvesAssignablePublicMethodAfterExactLookupMiss() {
        MethodCallExpression call = EnumUtils.call(
            null,
            Sources.class,
            "of",
            arguments(Expressions.constant("inline source", String.class)));

        assertThat(call.method.getName()).isEqualTo("of");
        assertThat(call.method.getDeclaringClass()).isEqualTo(Sources.class);
        assertThat(call.method.getParameterTypes()).containsExactly(CharSequence.class);
        assertThat(call.getType()).isEqualTo(Source.class);
    }

    private static List<Expression> arguments(Expression expression) {
        return Collections.singletonList(expression);
    }
}
