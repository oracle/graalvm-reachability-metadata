/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.elasticsearch.license.GetLicenseRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestBaseTest {
    @Test
    void formatsEndpointDetailsFromGeneratedRequest() {
        GetLicenseRequest request = GetLicenseRequest.of(builder -> builder.local(true));

        String description = request.toString();

        assertThat(description).isEqualTo("GetLicenseRequest: GET /_license?local=true");
    }
}
