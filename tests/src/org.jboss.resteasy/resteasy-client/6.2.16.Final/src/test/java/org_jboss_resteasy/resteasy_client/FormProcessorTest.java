/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.AbortedResponse;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.junit.jupiter.api.Test;

class FormProcessorTest {

    @Test
    void beanParamUsesAnnotatedFieldsAndGettersToPopulateFormEntity() {
        RecordingClientHttpEngine engine = new RecordingClientHttpEngine();
        ResteasyClient client = new ResteasyClientBuilderImpl()
                .httpEngine(engine)
                .build();

        try {
            FormClient proxy = client.target("http://localhost:8080").proxy(FormClient.class);

            try (Response response = proxy.submit(new FormRequest("field-data", "getter-data"))) {
                assertThat(response.getStatus()).isEqualTo(204);
            }
            assertThat(engine.capturedInvocation).isNotNull();
            assertThat(engine.capturedInvocation.getEntity()).isInstanceOf(Form.class);

            Form form = (Form) engine.capturedInvocation.getEntity();
            assertThat(form.asMap()).containsEntry("fieldValue", List.of("field-data"));
            assertThat(form.asMap()).containsEntry("getterValue", List.of("getter-data"));
        } finally {
            client.close();
        }
    }

    @Path("/forms")
    private interface FormClient {

        @POST
        Response submit(@BeanParam FormRequest request);
    }

    private static final class FormRequest {
        @FormParam("fieldValue")
        private final String fieldValue;

        private final String getterValue;

        private FormRequest(final String fieldValue, final String getterValue) {
            this.fieldValue = fieldValue;
            this.getterValue = getterValue;
        }

        @FormParam("getterValue")
        private String getGetterValue() {
            return getterValue;
        }
    }

    private static final class RecordingClientHttpEngine implements ClientHttpEngine {
        private ClientInvocation capturedInvocation;

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
            return new AbortedResponse(capturedInvocation.getClientConfiguration(), Response.noContent().build());
        }

        @Override
        public void close() {
        }
    }
}
