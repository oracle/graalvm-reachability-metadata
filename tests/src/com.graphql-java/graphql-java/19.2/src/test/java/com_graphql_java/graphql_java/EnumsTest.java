/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.base.Enums;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    @Test
    void locatesAnnotatedEnumConstantField() {
        Field field = Enums.getField(Operation.QUERY);

        assertThat(field.getName()).isEqualTo("QUERY");
        assertThat(field.getDeclaringClass()).isSameAs(Operation.class);
        assertThat(field.getAnnotation(GraphQlName.class).value()).isEqualTo("query");
    }

    private enum Operation {
        @GraphQlName("query")
        QUERY,
        MUTATION
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface GraphQlName {
        String value();
    }
}
