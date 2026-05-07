/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.elasticsearch.query_rules.ListRulesetsRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestBaseTest {
    @Test
    void toStringUsesEndpointDeclaredOnRequestClass() {
        ListRulesetsRequest request = ListRulesetsRequest.of(builder -> builder.size(25));

        String description = request.toString();

        assertThat(description).isEqualTo("ListRulesetsRequest: GET /_query_rules?size=25");
    }
}
