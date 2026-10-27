/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_restclient_test;

import org.junit.jupiter.api.Test;

import org.springframework.boot.restclient.test.MockServerRestClientCustomizer;
import org.springframework.boot.restclient.test.autoconfigure.MockRestServiceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class MockRestServiceServerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockRestServiceServerAutoConfiguration.class)
            .withPropertyValues("spring.test.restclient.mockrestserviceserver.enabled=true");

    @Test
    void autoConfiguredServerHandlesRequestFromCustomizedRestClient() {
        this.contextRunner.run(context -> {
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);
            MockServerRestClientCustomizer customizer = context.getBean(MockServerRestClientCustomizer.class);
            RestClient.Builder builder = RestClient.builder();
            customizer.customize(builder);
            server.expect(requestTo("https://example.test/greeting"))
                    .andRespond(withSuccess("hello", MediaType.TEXT_PLAIN));

            String response = builder.build()
                    .get()
                    .uri("https://example.test/greeting")
                    .retrieve()
                    .body(String.class);

            assertThat(response).isEqualTo("hello");
            server.verify();
        });
    }
}
