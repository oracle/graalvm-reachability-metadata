/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathImpl;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.Visitor;
import com.querydsl.core.types.dsl.BeanPath;
import com.querydsl.core.types.dsl.CollectionPath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathInits;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionPathBaseTest {
    @Test
    void anyCreatesTypedPathWithPathInitsConstructor() {
        CollectionPath<Object, BeanPath<Object>> path = beanPathCollection("typedWithInits");

        BeanPath<Object> any = path.any();

        assertCollectionAnyPath(any);
        assertThat(any.getType()).isEqualTo(Object.class);
        assertThat(path.any()).isSameAs(any);
    }

    @Test
    void anyCreatesTypedPathWithMetadataOnlyConstructor() {
        CollectionPath<Object, SimplePath<Object>> path = simplePathCollection("typedWithMetadataOnly");

        SimplePath<Object> any = path.any();

        assertCollectionAnyPath(any);
        assertThat(any.getType()).isEqualTo(Object.class);
        assertThat(path.any()).isSameAs(any);
    }

    @Test
    void anyCreatesUntypedPathWithPathInitsConstructor() {
        CollectionPath<Object, PathInitsAwareExpression> path = Expressions.collectionPath(
                Object.class,
                PathInitsAwareExpression.class,
                PathMetadataFactory.forVariable("untypedWithInits"));

        PathInitsAwareExpression any = path.any();

        assertCollectionAnyPath(any);
        assertThat(any.getUsedInits()).isSameAs(PathInits.DIRECT);
        assertThat(path.any()).isSameAs(any);
    }

    @Test
    void anyCreatesUntypedPathWithMetadataOnlyConstructor() {
        CollectionPath<String, StringPath> path = Expressions.collectionPath(
                String.class,
                StringPath.class,
                PathMetadataFactory.forVariable("untypedWithMetadataOnly"));

        StringPath any = path.any();

        assertCollectionAnyPath(any);
        assertThat(any.getType()).isEqualTo(String.class);
        assertThat(path.any()).isSameAs(any);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CollectionPath<Object, BeanPath<Object>> beanPathCollection(String variable) {
        return Expressions.collectionPath(
                Object.class,
                (Class) BeanPath.class,
                PathMetadataFactory.forVariable(variable));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CollectionPath<Object, SimplePath<Object>> simplePathCollection(String variable) {
        return Expressions.collectionPath(
                Object.class,
                (Class) SimplePath.class,
                PathMetadataFactory.forVariable(variable));
    }

    private static void assertCollectionAnyPath(Path<?> path) {
        assertThat(path.getMetadata().getPathType()).isEqualTo(PathType.COLLECTION_ANY);
        assertThat(path.getMetadata().getParent()).isNotNull();
    }

    public static class PathInitsAwareExpression extends SimpleExpression<Object> implements Path<Object> {
        private static final long serialVersionUID = 1L;

        private final PathInits usedInits;
        private final PathImpl<Object> pathMixin;

        public PathInitsAwareExpression(PathMetadata metadata, PathInits inits) {
            super(ExpressionUtils.path(Object.class, metadata));
            this.pathMixin = (PathImpl<Object>) mixin;
            this.usedInits = inits;
        }

        public PathInits getUsedInits() {
            return usedInits;
        }

        @Override
        public <R, C> R accept(Visitor<R, C> visitor, C context) {
            return visitor.visit(pathMixin, context);
        }

        @Override
        public PathMetadata getMetadata() {
            return pathMixin.getMetadata();
        }

        @Override
        public Path<?> getRoot() {
            return pathMixin.getRoot();
        }

        @Override
        public AnnotatedElement getAnnotatedElement() {
            return pathMixin.getAnnotatedElement();
        }
    }
}
