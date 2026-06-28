/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.JsonpMapperBase;
import org.junit.jupiter.api.Test;

public class JsonpMapperBaseTest {
    @Test
    void findDeserializerReturnsGeneratedDeserializerForJsonpDeserializableType() {
        JsonpDeserializer<InfoResponse> deserializer = JsonpMapperBase.findDeserializer(InfoResponse.class);

        assertThat(deserializer).isSameAs(InfoResponse._DESERIALIZER);
    }
}
