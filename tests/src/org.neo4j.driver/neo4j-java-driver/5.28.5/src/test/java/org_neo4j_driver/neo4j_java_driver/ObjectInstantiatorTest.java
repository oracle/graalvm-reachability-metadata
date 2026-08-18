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

public class ObjectInstantiatorTest {
    @Test
    void instantiatesMappedObjectWithConvertedConstructorArguments() {
        Value value = Values.value(Map.of("name", "Trinity", "crewRank", 1, "active", true));

        CrewMember crewMember = value.as(CrewMember.class);

        assertThat(crewMember.name()).isEqualTo("Trinity");
        assertThat(crewMember.rank()).isEqualTo(1);
        assertThat(crewMember.active()).isTrue();
    }

    public record CrewMember(String name, @Property("crewRank") int rank, boolean active) {}
}
