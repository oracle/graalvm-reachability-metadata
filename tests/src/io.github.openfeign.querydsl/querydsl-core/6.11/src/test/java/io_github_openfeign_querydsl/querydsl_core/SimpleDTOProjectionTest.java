/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.SimpleDTOProjection;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.StringPath;
import org.junit.jupiter.api.Test;

public class SimpleDTOProjectionTest {

    @Test
    void fieldsBuildsProjectionFromMatchingEntityPath() {
        PersonPath person = new PersonPath("person");
        SimpleDTOProjection<PersonSummary> projection = SimpleDTOProjection.fields(PersonSummary.class, person);

        PersonSummary summary = projection.newInstance("Ada");

        assertThat(projection.getArgs()).containsExactly(person.name);
        assertThat(summary.name()).isEqualTo("Ada");
    }

    public static final class PersonPath extends EntityPathBase<PersonPath> {

        public final StringPath name = createString("name");

        public PersonPath(String variable) {
            super(PersonPath.class, variable);
        }
    }

    public static final class PersonSummary {

        private final String name;

        public PersonSummary(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }
}
