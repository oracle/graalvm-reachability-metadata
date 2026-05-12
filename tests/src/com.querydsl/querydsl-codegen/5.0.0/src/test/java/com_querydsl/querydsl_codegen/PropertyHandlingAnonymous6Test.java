/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_codegen;

import com.querydsl.codegen.PropertyHandling;
import com.querydsl.core.annotations.PropertyType;
import com.querydsl.core.annotations.QueryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyHandlingAnonymous6Test {

    @Test
    void jpaHandlingInspectsDeclaredFieldsAndMethods() {
        PropertyHandling.Config config = PropertyHandling.JPA.getConfig(QuerydslAnnotatedAccessors.class);

        assertThat(config).isEqualTo(PropertyHandling.Config.ALL);
    }

    private static final class QuerydslAnnotatedAccessors {

        @QueryType(PropertyType.STRING)
        private String fieldAnnotatedProperty;

        private String methodAnnotatedProperty;

        @QueryType(PropertyType.STRING)
        public String getMethodAnnotatedProperty() {
            return methodAnnotatedProperty;
        }
    }
}
