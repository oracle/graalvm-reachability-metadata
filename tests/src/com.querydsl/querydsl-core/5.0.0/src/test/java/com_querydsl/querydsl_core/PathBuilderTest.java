/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.ArrayPath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.PathBuilderValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PathBuilderTest {
    @Test
    void getArrayCreatesPropertyPathForArrayField() {
        PathBuilder<Document> document = new PathBuilder<>(Document.class, "document", PathBuilderValidator.FIELDS);

        ArrayPath<String, String> tags = document.getArray("tags", String.class);

        assertThat(tags.getMetadata().getName()).isEqualTo("tags");
        assertThat(tags.getMetadata().getParent()).isSameAs(document);
        assertThat(tags.getMetadata().getPathType()).isEqualTo(PathType.PROPERTY);
        assertThat(tags.get(0).getMetadata().getParent()).isEqualTo(tags);
    }

    public static class Document {
        private String[] tags;
    }
}
