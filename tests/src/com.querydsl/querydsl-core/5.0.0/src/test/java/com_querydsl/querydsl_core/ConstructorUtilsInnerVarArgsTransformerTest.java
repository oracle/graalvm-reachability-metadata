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
    void constructorExpressionPacksTrailingArgumentsIntoVarArgsArray() {
        ConstructorExpression<AuditEvent> projection = Projections.constructor(
                AuditEvent.class,
                new Class<?>[] {String.class, String.class, String.class},
                Expressions.stringPath("name"),
                Expressions.stringPath("firstTag"),
                Expressions.stringPath("secondTag"));

        AuditEvent event = projection.newInstance("created", "query", "core");

        assertThat(event.getName()).isEqualTo("created");
        assertThat(event.getTags()).containsExactly("query", "core");
    }

    public static class AuditEvent {

        private final String name;

        private final List<String> tags;

        public AuditEvent(String name, String... tags) {
            this.name = name;
            this.tags = Arrays.asList(tags);
        }

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return tags;
        }
    }
}
