/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.BeanPath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.ListPath;
import com.querydsl.core.types.dsl.PathInits;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;

public class CollectionPathBaseTest {
    @Test
    void anyCreatesTypedElementPathWithPathInitsConstructor() {
        ListPath<String, BeanPath<String>> path = listPath(BeanPath.class, "beans");

        BeanPath<String> any = path.any();

        assertCollectionAnyPath(any.getMetadata());
        assertSame(any, path.any());
    }

    @Test
    void anyCreatesTypedElementPathWithoutPathInitsConstructor() {
        ListPath<String, SimplePath<String>> path = listPath(SimplePath.class, "simpleValues");

        SimplePath<String> any = path.any();

        assertCollectionAnyPath(any.getMetadata());
        assertSame(any, path.any());
    }

    @Test
    void anyCreatesUntypedElementPathWithPathInitsConstructor() {
        ListPath<String, NonTypedPathWithInits> path = listPath(NonTypedPathWithInits.class,
                "initializedValues");

        NonTypedPathWithInits any = path.any();

        assertCollectionAnyPath(any.getMetadata());
        assertSame(PathInits.DIRECT, any.getInits());
        assertSame(any, path.any());
    }

    @Test
    void anyCreatesUntypedElementPathWithoutPathInitsConstructor() {
        ListPath<String, StringPath> path = listPath(StringPath.class, "stringValues");

        StringPath any = path.any();

        assertCollectionAnyPath(any.getMetadata());
        assertSame(any, path.any());
    }

    private static void assertCollectionAnyPath(PathMetadata metadata) {
        assertEquals(PathType.COLLECTION_ANY, metadata.getPathType());
        assertNotNull(metadata.getParent());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <Q extends SimpleExpression<? super String>> ListPath<String, Q> listPath(Class<?> queryType,
            String variable) {
        return Expressions.listPath(String.class, (Class) queryType, PathMetadataFactory.forVariable(variable));
    }

    public static class NonTypedPathWithInits extends StringPath {
        private final PathInits inits;

        public NonTypedPathWithInits(PathMetadata metadata, PathInits inits) {
            super(metadata);
            this.inits = inits;
        }

        PathInits getInits() {
            return inits;
        }
    }
}
