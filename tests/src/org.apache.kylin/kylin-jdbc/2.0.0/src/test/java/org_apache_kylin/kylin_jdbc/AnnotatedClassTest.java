/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

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
