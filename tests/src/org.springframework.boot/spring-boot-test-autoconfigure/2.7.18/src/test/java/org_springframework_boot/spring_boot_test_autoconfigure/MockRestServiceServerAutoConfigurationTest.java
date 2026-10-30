/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.client.MockRestServiceServerAutoConfiguration;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class MockRestServiceServerAutoConfigurationTest {

    @Test
    void createsServerBeforeRestTemplateAndDelegatesRequestsToItsCustomizer() {
        MockRestServiceServerAutoConfiguration configuration = new MockRestServiceServerAutoConfiguration();
        MockServerRestTemplateCustomizer customizer = configuration.mockServerRestTemplateCustomizer();
        MockRestServiceServer server = configuration.mockRestServiceServer(customizer);
        RestTemplate client = new RestTemplateBuilder().additionalCustomizers(customizer).build();

        server.expect(requestTo("https://inventory.example/items/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("item-42:available", MediaType.TEXT_PLAIN));

        assertThat(client.getForObject("https://inventory.example/items/42", String.class))
                .isEqualTo("item-42:available");
        server.verify();
    }
}
