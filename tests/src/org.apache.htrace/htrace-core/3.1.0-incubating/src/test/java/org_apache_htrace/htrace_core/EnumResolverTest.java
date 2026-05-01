/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonValue;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumResolverTest {
    @Test
    void deserializesEnumUsingJsonValueMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        SpanRelationship relationship = mapper.readValue("\"child-span\"", SpanRelationship.class);

        assertThat(relationship).isSameAs(SpanRelationship.CHILD);
        assertThat(mapper.readValue("\"root-span\"", SpanRelationship.class)).isSameAs(SpanRelationship.ROOT);
    }

    public enum SpanRelationship {
        ROOT("root-span"),
        CHILD("child-span");

        private final String jsonValue;

        SpanRelationship(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        @JsonValue
        public String asJsonValue() {
            return jsonValue;
        }
    }
}
