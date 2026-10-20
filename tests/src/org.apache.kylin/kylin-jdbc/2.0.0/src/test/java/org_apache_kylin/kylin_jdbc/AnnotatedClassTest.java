/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    @Test
    void introspectsConstructorsFieldsAndMethodsDuringBeanRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Customer input = new Customer();
        input.publicId = 17;
        input.setName("Ada");

        String json = mapper.writeValueAsString(input);
        Customer output = mapper.readValue(json, Customer.class);

        assertThat(output.publicId).isEqualTo(17);
        assertThat(output.getName()).isEqualTo("Ada");
    }

    @Test
    void appliesConstructorFieldFactoryAndMethodMixIns() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixInAnnotations(MixedCustomer.class, CustomerMixIn.class);

        MixedCustomer created = mapper.readValue("{\"identifier\":23,\"displayName\":\"Grace\"}", MixedCustomer.class);
        String json = mapper.writeValueAsString(created);

        assertThat(created.id).isEqualTo(23);
        assertThat(created.name).isEqualTo("Grace");
        assertThat(json).contains("\"identifier\":23", "\"displayName\":\"Grace\"");
    }

    public abstract static class CustomerMixIn {
        @JsonCreator
        public CustomerMixIn(@JsonProperty("identifier") int id) {
        }

        @JsonProperty("identifier")
        public int id;

        @JsonProperty("displayName")
        public abstract String getName();

        @JsonProperty("displayName")
        public abstract void setName(String name);

        public static MixedCustomer valueOf(@JsonProperty("identifier") String id) {
            return null;
        }
    }

    public static class MixedCustomer {
        public int id;
        private String name;

        public MixedCustomer(int id) {
            this.id = id;
        }

        public static MixedCustomer valueOf(String id) {
            return new MixedCustomer(Integer.parseInt(id));
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Customer {
        public int publicId;
        private String name;

        public Customer() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
