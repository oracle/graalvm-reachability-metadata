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

public class AnnotatedFieldCollectorTest {
    @Test
    void appliesFieldMixInAndInheritedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Customer.class, CustomerMixIn.class);
        Customer customer = mapper.readValue("{\"display_name\":\"Sam\",\"id\":9}", Customer.class);
        assertEquals("Sam", customer.name);
        assertEquals(9, customer.id);
        assertEquals("Sam", mapper.readTree(mapper.writeValueAsString(customer)).get("display_name").asText());
    }

    public static class Identified {
        public int id;
    }

    public static class Customer extends Identified {
        public String name;
    }

    public abstract static class CustomerMixIn {
        @JsonProperty("display_name")
        public String name;
    }
}
