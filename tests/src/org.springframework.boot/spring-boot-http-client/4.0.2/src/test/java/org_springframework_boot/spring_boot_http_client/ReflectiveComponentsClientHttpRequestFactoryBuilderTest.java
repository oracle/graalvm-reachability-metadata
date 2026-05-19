/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_client;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveComponentsClientHttpRequestFactoryBuilderTest {

    @Test
    void buildsCustomRequestFactoryUsingReflectiveConstructor() {
        ClientHttpRequestFactoryBuilder<CustomClientHttpRequestFactory> builder = ClientHttpRequestFactoryBuilder
            .of(CustomClientHttpRequestFactory.class);

        CustomClientHttpRequestFactory requestFactory = builder.build();

        assertThat(requestFactory).isNotNull();
    }

}

final class CustomClientHttpRequestFactory implements ClientHttpRequestFactory {

    CustomClientHttpRequestFactory() {
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
        throw new UnsupportedOperationException("Request creation is not needed for builder coverage");
    }

}
