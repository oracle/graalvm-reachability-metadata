/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.enumerable.EnumUtils;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumUtilsTest {
    @Test
    void resolvesExactAndAssignableMethods() {
        MethodCallExpression exact = EnumUtils.call(
                null, Methods.class, "exact", List.of(Expressions.constant("value")));
        MethodCallExpression assignable = EnumUtils.call(
                null, Methods.class, "number", List.of(Expressions.constant(3, Integer.class)));

        assertThat(exact.method.getName()).isEqualTo("exact");
        assertThat(assignable.method.getName()).isEqualTo("number");
        assertThat(assignable.method.getParameterTypes()).containsExactly(Number.class);
    }

    public static class Methods {
        public static String exact(String value) {
            return value;
        }

        public static Number number(Number value) {
            return value;
        }
    }
}
