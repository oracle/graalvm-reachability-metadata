/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Path;
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
import java.lang.reflect.AnnotatedElement;
import org.junit.jupiter.api.Test;

public class CollectionPathBaseTest {

    @Test
    void anyCreatesTypedPathWithPathInitsConstructor() {
        CollectionPath<String, BeanPath<String>> path = collectionPath("typedBeans", BeanPath.class);

        BeanPath<String> any = path.any();

        assertCollectionAnyPath(any, path);
    }

    @Test
    void anyCreatesTypedPathWithPathMetadataConstructor() {
        CollectionPath<String, SimplePath<String>> path = collectionPath("simplePaths", SimplePath.class);

        SimplePath<String> any = path.any();

        assertCollectionAnyPath(any, path);
    }

    @Test
    void anyCreatesNonTypedPathWithPathInitsConstructor() {
        CollectionPath<String, PathInitsAwareStringPath> path = collectionPath(
                "pathInitsAwareStrings", PathInitsAwareStringPath.class);

        PathInitsAwareStringPath any = path.any();

        assertCollectionAnyPath(any, path);
        assertThat(any.pathInits()).isSameAs(PathInits.DIRECT);
    }

    @Test
    void anyCreatesNonTypedPathWithPathMetadataConstructor() {
        CollectionPath<String, StringPath> path = collectionPath("stringPaths", StringPath.class);

        StringPath any = path.any();

        assertCollectionAnyPath(any, path);
    }

    private static void assertCollectionAnyPath(SimpleExpression<String> any, CollectionPath<String, ?> path) {
        assertThat(any.getType()).isEqualTo(String.class);
        assertThat(((Path<?>) any).getMetadata().getPathType()).isEqualTo(PathType.COLLECTION_ANY);
        assertThat(((Path<?>) any).getMetadata().getParent().getMetadata()).isEqualTo(path.getMetadata());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <Q extends SimpleExpression<? super String>> CollectionPath<String, Q> collectionPath(
            String variable, Class<?> queryType) {
        return Expressions.collectionPath(
                String.class,
                (Class) queryType,
                PathMetadataFactory.forVariable(variable));
    }
}

class PathInitsAwareStringPath extends SimpleExpression<String> implements Path<String> {

    private final PathInits pathInits;

    private final Path<String> pathMixin;

    @SuppressWarnings("unchecked")
    PathInitsAwareStringPath(PathMetadata metadata, PathInits pathInits) {
        super(ExpressionUtils.path(String.class, metadata));
        this.pathInits = pathInits;
        this.pathMixin = (Path<String>) mixin;
    }

    PathInits pathInits() {
        return pathInits;
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
