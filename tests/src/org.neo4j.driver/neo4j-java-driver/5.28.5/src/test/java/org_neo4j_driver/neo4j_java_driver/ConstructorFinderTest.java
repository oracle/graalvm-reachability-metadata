/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_driver.neo4j_java_driver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.mapping.Property;

public class ConstructorFinderTest {
    @Test
    void mapsValueToObjectUsingAnnotatedPublicConstructor() {
        Value value = Values.value(Map.of(
                "title", "The Matrix",
                "releasedYear", 1999L));

        MovieProjection movie = value.as(MovieProjection.class);

        assertThat(movie.title()).isEqualTo("The Matrix");
        assertThat(movie.releasedYear()).isEqualTo(1999L);
    }

    public static final class MovieProjection {
        private final String title;
        private final long releasedYear;

        public MovieProjection(@Property("title") String title, @Property("releasedYear") long releasedYear) {
            this.title = title;
            this.releasedYear = releasedYear;
        }

        String title() {
            return title;
        }

        long releasedYear() {
            return releasedYear;
        }
    }
}
