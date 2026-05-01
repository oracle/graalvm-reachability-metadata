/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.ListPath;
import com.querydsl.core.types.dsl.PathInits;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;

import org.junit.jupiter.api.Test;

public class CollectionPathBaseTest {

    @Test
    void anyCreatesTypedPathWithPathInitsConstructor() {
        Class<EntityPathBase<Object>> queryType = queryType(EntityPathBase.class);
        ListPath<Object, EntityPathBase<Object>> path = Expressions.listPath(
                Object.class,
                queryType,
                metadata("typedWithInits"));

        EntityPathBase<Object> any = path.any();

        assertThat(any.getType()).isEqualTo(Object.class);
        assertThat(any.getMetadata().getPathType()).isEqualTo(PathType.COLLECTION_ANY);
    }

    @Test
    void anyCreatesTypedPathWithFallbackConstructor() {
        Class<SimplePath<Object>> queryType = queryType(SimplePath.class);
        ListPath<Object, SimplePath<Object>> path = Expressions.listPath(
                Object.class,
                queryType,
                metadata("typedWithoutInits"));

        SimplePath<Object> any = path.any();

        assertThat(any.getType()).isEqualTo(Object.class);
        assertThat(any.getMetadata().getPathType()).isEqualTo(PathType.COLLECTION_ANY);
    }

    @Test
    void anyCreatesUntypedPathWithPathInitsConstructor() {
        ListPath<Object, UntypedPathWithPathInits> path = Expressions.listPath(
                Object.class,
                UntypedPathWithPathInits.class,
                metadata("untypedWithInits"));

        UntypedPathWithPathInits any = path.any();

        assertThat(any.getType()).isEqualTo(Object.class);
        assertThat(any.getMetadata().getPathType()).isEqualTo(PathType.COLLECTION_ANY);
        assertThat(any.getPathInits()).isSameAs(PathInits.DIRECT);
    }

    @Test
    void anyCreatesUntypedPathWithFallbackConstructor() {
        ListPath<String, StringPath> path = Expressions.listPath(
                String.class,
                StringPath.class,
                metadata("untypedWithoutInits"));

        StringPath any = path.any();

        assertThat(any.getType()).isEqualTo(String.class);
        assertThat(any.getMetadata().getPathType()).isEqualTo(PathType.COLLECTION_ANY);
    }

    private static PathMetadata metadata(String variable) {
        return PathMetadataFactory.forVariable(variable);
    }

    @SuppressWarnings("unchecked")
    private static <Q> Class<Q> queryType(Class<?> type) {
        return (Class<Q>) type;
    }
}

final class UntypedPathWithPathInits extends SimplePath<Object> {

    private final PathInits pathInits;

    UntypedPathWithPathInits(PathMetadata metadata, PathInits pathInits) {
        super(Object.class, metadata);
        this.pathInits = pathInits;
    }

    PathInits getPathInits() {
        return pathInits;
    }
}
