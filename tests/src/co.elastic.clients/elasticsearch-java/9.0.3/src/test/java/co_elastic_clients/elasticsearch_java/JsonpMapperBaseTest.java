/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.elasticsearch.query_rules.QueryRuleType;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.JsonpMapperBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonpMapperBaseTest {
    @Test
    void findDeserializerUsesJsonpDeserializableField() {
        JsonpDeserializer<QueryRuleType> deserializer = JsonpMapperBase.findDeserializer(QueryRuleType.class);

        assertThat(deserializer).isSameAs(QueryRuleType._DESERIALIZER);
    }
}
