/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.DeserializationFeature;
import shaded.parquet.com.fasterxml.jackson.databind.JsonNode;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectArrayDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Test
    void wrapsSingleObjectValueInTypedObjectArray() throws Exception {
        JsonNode[] nodes = MAPPER.readValue("""
                {
                  "name": "daily-summary",
                  "priority": 5
                }
                """, JsonNode[].class);

        assertThat(nodes).hasSize(1);
        assertThat(nodes[0].get("name").asText()).isEqualTo("daily-summary");
        assertThat(nodes[0].get("priority").asInt()).isEqualTo(5);
    }
}
