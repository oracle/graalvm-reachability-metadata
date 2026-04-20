/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.annotations.ResponseObject;
import org.jboss.resteasy.annotations.Status;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.AbortedResponse;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.junit.jupiter.api.Test;

class ResponseObjectProxyTest {

    @Test
    void responseObjectReturnTypeBuildsMethodHandlersAndCreatesProxyInstance() {
        final RecordingClientHttpEngine engine = new RecordingClientHttpEngine();

        try (ResteasyClient client = new ResteasyClientBuilderImpl()
                .httpEngine(engine)
                .build()) {
            final ResponseObjectClient proxy = client.target("http://localhost:8080").proxy(ResponseObjectClient.class);

            final RichResponse richResponse = proxy.fetch();

            try (Response rawResponse = richResponse.rawResponse()) {
                assertThat(richResponse.status()).isEqualTo(201);
                assertThat(richResponse.requestId()).isEqualTo("request-123");
                assertThat(rawResponse.getStatus()).isEqualTo(201);
            }
        }

        assertThat(engine.capturedInvocation).isNotNull();
    }
}

@Path("/responses")
interface ResponseObjectClient {

    @GET
    @Path("/current")
    RichResponse fetch();
}

@ResponseObject
interface RichResponse {

    @Status
    int status();

    @HeaderParam("X-Request-Id")
    String requestId();

    Response rawResponse();
}

final class RecordingClientHttpEngine implements ClientHttpEngine {
    ClientInvocation capturedInvocation;

    @Override
    public SSLContext getSslContext() {
        return null;
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return null;
    }

    @Override
    public Response invoke(final Invocation invocation) {
        capturedInvocation = (ClientInvocation) invocation;
        return new AbortedResponse(
                capturedInvocation.getClientConfiguration(),
                Response.status(201)
                        .header("X-Request-Id", "request-123")
                        .build());
    }

    @Override
    public void close() {
    }
}
