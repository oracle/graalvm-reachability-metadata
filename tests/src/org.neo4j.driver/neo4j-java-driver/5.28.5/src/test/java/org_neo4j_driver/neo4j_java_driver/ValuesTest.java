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

public class ValuesTest {
    @Test
    void mapsRecordComponentsToMapValue() {
        Movie movie = new Movie("The Matrix", "Welcome to the Real World", 1999, null);

        Value value = Values.value(movie);

        assertThat(value.size()).isEqualTo(3);
        assertThat(value.get("title").asString()).isEqualTo("The Matrix");
        assertThat(value.get("tagline").asString()).isEqualTo("Welcome to the Real World");
        assertThat(value.get("releasedYear").asLong()).isEqualTo(1999L);
        assertThat(value.containsKey("internalNote")).isFalse();
        assertThat(value.asMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "title", "The Matrix",
                "tagline", "Welcome to the Real World",
                "releasedYear", 1999L));
    }

    public record Movie(String title, String tagline, @Property("releasedYear") long released, String internalNote) {}
}
