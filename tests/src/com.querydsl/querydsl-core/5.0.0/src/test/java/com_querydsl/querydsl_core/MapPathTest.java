/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.MapPath;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;
import org.junit.jupiter.api.Test;

public class MapPathTest {

    @Test
    void getCreatesTypedPathWithClassAndPathMetadataConstructor() {
        MapPath<String, Object, SimplePath<Object>> path = mapPath("typedValues", Object.class, SimplePath.class);

        SimplePath<Object> value = path.get("first");

        assertMapValuePath(value, path, Object.class, PathType.MAPVALUE_CONSTANT, "first");
    }

    @Test
    void getCreatesNonTypedPathWithPathMetadataConstructor() {
        MapPath<String, String, StringPath> path = mapPath("stringValues", String.class, StringPath.class);
        Expression<String> key = Expressions.stringPath("dynamicKey");

        StringPath value = path.get(key);

        assertMapValuePath(value, path, String.class, PathType.MAPVALUE, key);
    }

    private static void assertMapValuePath(
            SimpleExpression<?> value,
            MapPath<?, ?, ?> path,
            Class<?> valueType,
            PathType pathType,
            Object key) {
        PathMetadata metadata = ((Path<?>) value).getMetadata();

        assertThat(value.getType()).isEqualTo(valueType);
        assertThat(metadata.getPathType()).isEqualTo(pathType);
        assertThat(metadata.getParent().getMetadata()).isEqualTo(path.getMetadata());
        assertThat(metadata.getElement()).isEqualTo(key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <K, V, E extends SimpleExpression<? super V>> MapPath<K, V, E> mapPath(
            String variable,
            Class<V> valueType,
            Class<?> queryType) {
        return Expressions.mapPath(
                String.class,
                valueType,
                (Class) queryType,
                PathMetadataFactory.forVariable(variable));
    }
}
