/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.apache.calcite.adapter.enumerable.EnumUtils;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.junit.jupiter.api.Test;

public class EnumUtilsTest {
    @Test
    void callResolvesMethodWhenArgumentTypeIsAssignableToParameterType() {
        MethodCallExpression call = EnumUtils.call(
                null,
                MethodHost.class,
                "format",
                Collections.singletonList(Expressions.constant("calcite")));

        assertThat(call.targetExpression).isNull();
        assertThat(call.getType()).isEqualTo(String.class);
        assertThat(call.expressions).hasSize(1);
        assertThat(call.expressions.get(0).getType()).isEqualTo(String.class);
    }

    public static class MethodHost {
        public static String format(CharSequence value) {
            return "[" + value + "]";
        }
    }
}
