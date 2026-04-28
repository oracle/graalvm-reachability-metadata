/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.BeanPath;
import com.querydsl.core.types.dsl.PathInits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPathTest {
    @Test
    void asUsesMetadataOnlyConstructorForSubtypePath() {
        BeanPath<Object> path = new BeanPath<>(Object.class, "customer");

        TestBeanPath cast = path.as(TestBeanPath.class);

        assertThat(cast.getUsedInits()).isNull();
        assertThat(cast.getMetadata().getPathType()).isEqualTo(PathType.DELEGATE);
        assertThat(cast.getMetadata().getParent()).isEqualTo(path);
        assertThat(path.as(TestBeanPath.class)).isSameAs(cast);
    }

    @Test
    void asUsesPathInitsConstructorForInitializedNonVariablePath() {
        BeanPath<Object> root = new BeanPath<>(Object.class, "customer");
        BeanPath<Object> property = new BeanPath<>(
                Object.class,
                PathMetadataFactory.forProperty(root, "address"),
                PathInits.DIRECT);

        TestBeanPath cast = property.as(TestBeanPath.class);

        assertThat(cast.getUsedInits()).isSameAs(PathInits.DIRECT);
        assertThat(cast.getMetadata().getPathType()).isEqualTo(PathType.DELEGATE);
        assertThat(cast.getMetadata().getParent()).isEqualTo(property);
        assertThat(property.as(TestBeanPath.class)).isSameAs(cast);
    }

    public static class TestBeanPath extends BeanPath<Object> {
        private static final long serialVersionUID = 1L;

        private final PathInits usedInits;

        public TestBeanPath(PathMetadata metadata) {
            super(Object.class, metadata);
            this.usedInits = null;
        }

        public TestBeanPath(PathMetadata metadata, PathInits inits) {
            super(Object.class, metadata, inits);
            this.usedInits = inits;
        }

        public PathInits getUsedInits() {
            return usedInits;
        }
    }
}
