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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class MockRestServiceServerAutoConfigurationTest {

    @Test
    void createsDeferredMockRestServiceServerAndBindsToCustomizedRestTemplate() {
        MockRestServiceServerAutoConfiguration autoConfiguration = new MockRestServiceServerAutoConfiguration();
        MockServerRestTemplateCustomizer customizer = autoConfiguration.mockServerRestTemplateCustomizer();

        MockRestServiceServer server = autoConfiguration.mockRestServiceServer(customizer);
        RestTemplate restTemplate = new RestTemplate();
        customizer.customize(restTemplate);

        server.expect(requestTo("https://example.test/greeting"))
                .andRespond(withSuccess("hello", MediaType.TEXT_PLAIN));

        String body = restTemplate.getForObject("https://example.test/greeting", String.class);

        assertThat(body).isEqualTo("hello");
        server.verify();
        server.reset();
    }

}
