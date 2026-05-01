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
    void asCreatesAndCachesSubtypeWithMetadataConstructor() {
        BeanPath<Person> path = new BeanPath<Person>(Person.class, "person");

        QueryPerson queryPerson = path.as(QueryPerson.class);
        QueryPerson cachedQueryPerson = path.as(QueryPerson.class);

        assertThat(queryPerson).isSameAs(cachedQueryPerson);
        assertThat(queryPerson.getType()).isEqualTo(Person.class);
        assertThat(queryPerson.getMetadata().getPathType()).isEqualTo(PathType.DELEGATE);
    }

    @Test
    void asCreatesSubtypeWithPathInitsConstructorForNonVariablePath() {
        BeanPath<Person> root = new BeanPath<Person>(Person.class, "person");
        PathMetadata propertyMetadata = PathMetadataFactory.forProperty(root, "friend");
        BeanPath<Person> path = new BeanPath<Person>(Person.class, propertyMetadata, PathInits.DIRECT);

        QueryPersonWithInits queryPerson = path.as(QueryPersonWithInits.class);

        assertThat(queryPerson.getType()).isEqualTo(Person.class);
        assertThat(queryPerson.getMetadata().getPathType()).isEqualTo(PathType.DELEGATE);
        assertThat(queryPerson.getPathInits()).isSameAs(PathInits.DIRECT);
    }

    public static final class Person {
    }

    public static class QueryPerson extends BeanPath<Person> {

        private static final long serialVersionUID = 1L;

        public QueryPerson(PathMetadata metadata) {
            super(Person.class, metadata);
        }
    }

    public static class QueryPersonWithInits extends BeanPath<Person> {

        private static final long serialVersionUID = 1L;

        private final PathInits pathInits;

        public QueryPersonWithInits(PathMetadata metadata, PathInits pathInits) {
            super(Person.class, metadata, pathInits);
            this.pathInits = pathInits;
        }

        public PathInits getPathInits() {
            return pathInits;
        }
    }
}
