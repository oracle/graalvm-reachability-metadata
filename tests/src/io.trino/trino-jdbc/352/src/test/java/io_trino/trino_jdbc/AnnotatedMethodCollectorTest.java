/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonProperty;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedMethodCollectorTest {
    @Test
    void appliesAccessorMixIn() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Person.class, PersonMixIn.class);
        Person person = mapper.readValue("{\"full_name\":\"Rae\"}", Person.class);
        assertEquals("Rae", person.getName());
        assertEquals("Rae", mapper.readTree(mapper.writeValueAsString(person)).get("full_name").asText());
    }

    public static class Person {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public abstract static class PersonMixIn {
        @JsonProperty("full_name")
        public abstract String getName();

        @JsonProperty("full_name")
        public abstract void setName(String name);
    }
}
