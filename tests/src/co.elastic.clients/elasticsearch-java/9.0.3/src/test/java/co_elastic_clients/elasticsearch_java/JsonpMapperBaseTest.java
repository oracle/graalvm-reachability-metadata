/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.SimpleJsonpMapper;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonpMapperBaseTest {
    @Test
    void deserializesGeneratedApiTypeUsingAnnotationDeserializer() {
        String json = """
            {
              "cluster_name": "integration-cluster",
              "cluster_uuid": "cluster-uuid",
              "name": "node-1",
              "tagline": "You Know, for Search",
              "version": {
                "build_date": "2025-01-01T00:00:00Z",
                "build_flavor": "default",
                "build_hash": "abc123",
                "build_snapshot": false,
                "build_type": "docker",
                "lucene_version": "lucene-release",
                "minimum_index_compatibility_version": "compatible-index",
                "minimum_wire_compatibility_version": "compatible-wire",
                "number": "server-number"
              }
            }
            """;
        JsonpMapper mapper = new SimpleJsonpMapper();

        try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader(json))) {
            InfoResponse response = mapper.deserialize(parser, InfoResponse.class);

            assertThat(response.clusterName()).isEqualTo("integration-cluster");
            assertThat(response.version().number()).isEqualTo("server-number");
        }
    }
}
