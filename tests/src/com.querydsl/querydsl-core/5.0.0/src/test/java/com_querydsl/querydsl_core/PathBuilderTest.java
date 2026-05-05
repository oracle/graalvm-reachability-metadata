/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.ArrayPath;
import com.querydsl.core.types.dsl.PathBuilder;
import org.junit.jupiter.api.Test;

public class PathBuilderTest {

    @Test
    void getArrayCreatesTypedPathForArrayProperty() {
        PathBuilder<Object> entity = new PathBuilder<>(Object.class, "entity");

        ArrayPath<String[], String> names = entity.getArray("names", String[].class);

        assertThat(names.getType()).isEqualTo(String[].class);
        assertThat(names.getElementType()).isEqualTo(String.class);
        assertThat(names.getMetadata().getPathType()).isEqualTo(PathType.PROPERTY);
        assertThat(names.getMetadata().getName()).isEqualTo("names");
        assertThat(names.getMetadata().getParent()).isSameAs(entity);
    }
}
