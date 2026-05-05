/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.dsl.PathBuilderValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PathBuilderValidatorAnonymous2Test {

    @Test
    void fieldsValidatorResolvesDeclaredFieldTypes() {
        assertThat(PathBuilderValidator.FIELDS.validate(FieldBackedEntity.class, "name", Object.class))
                .isEqualTo(String.class);
        assertThat(PathBuilderValidator.FIELDS.validate(FieldBackedEntity.class, "active", Object.class))
                .isEqualTo(Boolean.class);
        assertThat(PathBuilderValidator.FIELDS.validate(FieldBackedEntity.class, "labels", Object.class))
                .isEqualTo(String.class);
        assertThat(PathBuilderValidator.FIELDS.validate(FieldBackedEntity.class, "ratings", Object.class))
                .isEqualTo(Integer.class);
    }

    @Test
    void fieldsValidatorSearchesInheritedFields() {
        assertThat(PathBuilderValidator.FIELDS.validate(FieldBackedEntity.class, "createdBy", Object.class))
                .isEqualTo(String.class);
    }

    private static class AuditedEntity {

        private String createdBy;
    }

    private static class FieldBackedEntity extends AuditedEntity {

        private String name;

        private boolean active;

        private List<String> labels;

        private Map<String, Integer> ratings;
    }
}
