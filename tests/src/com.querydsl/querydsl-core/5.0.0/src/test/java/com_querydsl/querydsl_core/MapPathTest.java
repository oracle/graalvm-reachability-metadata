/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.MapPath;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;

import org.junit.jupiter.api.Test;

public class MapPathTest {

    @Test
    void getWithConstantKeyCreatesTypedValuePath() {
        Class<SimplePath<Object>> queryType = queryType(SimplePath.class);
        MapPath<String, Object, SimplePath<Object>> path = Expressions.mapPath(
                String.class,
                Object.class,
                queryType,
                metadata("typedMap"));

        SimplePath<Object> value = path.get("customer");

        assertThat(value.getType()).isEqualTo(Object.class);
        assertThat(value.getMetadata().getParent()).isSameAs(path);
        assertThat(value.getMetadata().getElement()).isEqualTo("customer");
        assertThat(value.getMetadata().getPathType()).isEqualTo(PathType.MAPVALUE_CONSTANT);
        assertThat(path.getKeyType()).isEqualTo(String.class);
        assertThat(path.getValueType()).isEqualTo(Object.class);
    }

    @Test
    void getWithConstantKeyCreatesUntypedValuePath() {
        MapPath<String, String, StringPath> path = Expressions.mapPath(
                String.class,
                String.class,
                StringPath.class,
                metadata("untypedMap"));

        StringPath value = path.get("name");

        assertThat(value.getType()).isEqualTo(String.class);
        assertThat(value.getMetadata().getParent()).isSameAs(path);
        assertThat(value.getMetadata().getElement()).isEqualTo("name");
        assertThat(value.getMetadata().getPathType()).isEqualTo(PathType.MAPVALUE_CONSTANT);
        assertThat(path.getParameter(0)).isEqualTo(String.class);
        assertThat(path.getParameter(1)).isEqualTo(String.class);
    }

    @Test
    void getWithExpressionKeyCreatesMapValuePath() {
        MapPath<String, String, StringPath> path = Expressions.mapPath(
                String.class,
                String.class,
                StringPath.class,
                metadata("expressionMap"));
        Expression<String> key = Expressions.constant("keyExpression");

        StringPath value = path.get(key);

        assertThat(value.getType()).isEqualTo(String.class);
        assertThat(value.getMetadata().getParent()).isSameAs(path);
        assertThat(value.getMetadata().getElement()).isSameAs(key);
        assertThat(value.getMetadata().getPathType()).isEqualTo(PathType.MAPVALUE);
    }

    private static PathMetadata metadata(String variable) {
        return PathMetadataFactory.forVariable(variable);
    }

    @SuppressWarnings("unchecked")
    private static <Q> Class<Q> queryType(Class<?> type) {
        return (Class<Q>) type;
    }
}
