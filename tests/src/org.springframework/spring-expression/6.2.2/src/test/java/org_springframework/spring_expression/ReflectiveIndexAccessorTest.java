/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.junit.jupiter.api.Test;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectiveIndexAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveIndexAccessorTest {
    @Test
    void readsAndWritesIndexedValueThroughConfiguredMethods() {
        ReflectiveIndexAccessor accessor = new ReflectiveIndexAccessor(
                StringBuilder.class, int.class, "charAt", "setCharAt");
        StandardEvaluationContext context = new StandardEvaluationContext();
        StringBuilder text = new StringBuilder("cat");

        assertThat(accessor.canRead(context, text, 1))
                .isTrue();
        TypedValue initialValue = accessor.read(context, text, 1);
        assertThat(initialValue.getValue())
                .isEqualTo('a');

        assertThat(accessor.canWrite(context, text, 1))
                .isTrue();
        accessor.write(context, text, 1, 'o');

        assertThat(text)
                .hasToString("cot");
        assertThat(accessor.read(context, text, 1).getValue())
                .isEqualTo('o');
    }

    @Test
    void readOnlyAccessorDoesNotSupportWrites() {
        ReflectiveIndexAccessor accessor = new ReflectiveIndexAccessor(String.class, int.class, "charAt");
        StandardEvaluationContext context = new StandardEvaluationContext();

        assertThat(accessor.canRead(context, "spring", 0))
                .isTrue();
        assertThat(accessor.read(context, "spring", 0).getValue())
                .isEqualTo('s');
        assertThat(accessor.canWrite(context, "spring", 0))
                .isFalse();
    }
}
