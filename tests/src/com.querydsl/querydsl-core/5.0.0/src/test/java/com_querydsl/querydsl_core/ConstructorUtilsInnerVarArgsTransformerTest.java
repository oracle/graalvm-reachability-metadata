/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConstructorUtilsInnerVarArgsTransformerTest {

    @Test
    void constructorProjectionPacksReferenceVarArgs() {
        ConstructorExpression<TaggedValue> projection = Projections.constructor(
                TaggedValue.class,
                Expressions.constant("item"),
                Expressions.constant("blue"),
                Expressions.constant("large"));

        TaggedValue value = projection.newInstance("shirt", "blue", "large");

        assertThat(value.name()).isEqualTo("shirt");
        assertThat(value.tags()).containsExactly("blue", "large");
    }

    public static final class TaggedValue {

        private final String name;

        private final List<String> tags;

        public TaggedValue(String name, String... tags) {
            this.name = name;
            this.tags = Arrays.asList(tags);
        }

        String name() {
            return name;
        }

        List<String> tags() {
            return tags;
        }
    }
}
