/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorUtilsInnerVarArgsTransformerTest {
    @Test
    void constructorProjectionPacksTrailingArgumentsIntoVarArgsArray() {
        ConstructorExpression<TaggedValues> projection = Projections.constructor(
                TaggedValues.class,
                Expressions.stringPath("tag"),
                Expressions.stringPath("firstValue"),
                Expressions.stringPath("secondValue"));

        TaggedValues values = projection.newInstance("metadata", "querydsl", "native-image");

        assertThat(values.getTag()).isEqualTo("metadata");
        assertThat(values.getValues()).containsExactly("querydsl", "native-image");
    }

    public static final class TaggedValues {
        private final String tag;
        private final String[] values;

        public TaggedValues(String tag, String... values) {
            this.tag = tag;
            this.values = values.clone();
        }

        String getTag() {
            return tag;
        }

        List<String> getValues() {
            return Arrays.asList(values);
        }
    }
}
