/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.MapPath;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapPathTest {
    @Test
    void getWithConstantKeyCreatesTypedValuePath() {
        MapPath<String, Integer, SimplePath<Integer>> path = typedNumberMap("scoresByName");

        SimplePath<Integer> value = path.get("alice");

        assertThat(value.getType()).isEqualTo(Integer.class);
        assertThat(value.getMetadata().getPathType()).isEqualTo(PathType.MAPVALUE_CONSTANT);
        assertThat(value.getMetadata().getParent()).isSameAs(path);
        assertThat(value.getMetadata().getElement()).isEqualTo("alice");
    }

    @Test
    void getWithExpressionKeyCreatesUntypedValuePath() {
        MapPath<String, String, StringPath> path = stringMap("labelsByCode");
        Expression<String> key = Expressions.constant("primary");

        StringPath value = path.get(key);

        assertThat(value.getType()).isEqualTo(String.class);
        assertThat(value.getMetadata().getPathType()).isEqualTo(PathType.MAPVALUE);
        assertThat(value.getMetadata().getParent()).isSameAs(path);
        assertThat(value.getMetadata().getElement()).isSameAs(key);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MapPath<String, Integer, SimplePath<Integer>> typedNumberMap(String variable) {
        return Expressions.mapPath(
                String.class,
                Integer.class,
                (Class) SimplePath.class,
                PathMetadataFactory.forVariable(variable));
    }

    private static MapPath<String, String, StringPath> stringMap(String variable) {
        return Expressions.mapPath(
                String.class,
                String.class,
                StringPath.class,
                PathMetadataFactory.forVariable(variable));
    }
}
