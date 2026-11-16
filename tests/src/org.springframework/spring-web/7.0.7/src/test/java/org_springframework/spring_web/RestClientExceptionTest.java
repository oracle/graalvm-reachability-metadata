/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

public class RestClientExceptionTest {

    @Test
    void exposesServiceFailureFromRestClientRetrieval() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://catalog.example/items"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_PLAIN).body("catalog offline"));
        RestClient client = builder.build();

        assertThatThrownBy(() -> client.get().uri("https://catalog.example/items").retrieve().body(String.class))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("503")
                .hasMessageContaining("catalog offline");
        server.verify();
    }
}
