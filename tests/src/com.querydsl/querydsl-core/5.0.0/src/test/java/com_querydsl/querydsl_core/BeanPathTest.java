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
import com.querydsl.core.types.dsl.BeanPath;
import com.querydsl.core.types.dsl.PathInits;
import org.junit.jupiter.api.Test;

public class BeanPathTest {

    @Test
    void asCreatesSubtypeWithPathMetadataConstructor() {
        BeanPath<Object> root = new BeanPath<>(Object.class, "entity");

        OneArgumentEntityPath cast = root.as(OneArgumentEntityPath.class);
        OneArgumentEntityPath cached = root.as(OneArgumentEntityPath.class);

        assertThat(cast).isSameAs(cached);
        assertThat(cast.getType()).isEqualTo(Object.class);
        assertThat(cast.getMetadata().getPathType()).isEqualTo(PathType.DELEGATE);
        assertThat(cast.getMetadata().getParent().getMetadata()).isEqualTo(root.getMetadata());
    }

    @Test
    void asCreatesSubtypeWithPathMetadataAndPathInitsConstructor() {
        BeanPath<Object> root = new BeanPath<>(Object.class, "entity");
        BeanPath<Object> property = new BeanPath<>(
                Object.class,
                PathMetadataFactory.forProperty(root, "child"),
                PathInits.DIRECT);

        TwoArgumentEntityPath cast = property.as(TwoArgumentEntityPath.class);
        TwoArgumentEntityPath cached = property.as(TwoArgumentEntityPath.class);

        assertThat(cast).isSameAs(cached);
        assertThat(cast.pathInits()).isSameAs(PathInits.DIRECT);
        assertThat(cast.getType()).isEqualTo(Object.class);
        assertThat(cast.getMetadata().getPathType()).isEqualTo(PathType.DELEGATE);
        assertThat(cast.getMetadata().getParent().getMetadata()).isEqualTo(property.getMetadata());
    }

    public static class OneArgumentEntityPath extends BeanPath<Object> {

        public OneArgumentEntityPath(PathMetadata metadata) {
            super(Object.class, metadata);
        }
    }

    public static class TwoArgumentEntityPath extends BeanPath<Object> {

        private final PathInits pathInits;

        public TwoArgumentEntityPath(PathMetadata metadata, PathInits pathInits) {
            super(Object.class, metadata, pathInits);
            this.pathInits = pathInits;
        }

        PathInits pathInits() {
            return pathInits;
        }
    }
}
