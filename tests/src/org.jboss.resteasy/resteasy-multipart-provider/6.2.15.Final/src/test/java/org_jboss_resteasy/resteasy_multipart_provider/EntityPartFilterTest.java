/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_multipart_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.providers.StringTextStar;
import org.jboss.resteasy.plugins.providers.multipart.EntityPartFilter;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.multipart.MultipartContent;
import org.jboss.resteasy.util.DelegatingHttpRequest;
import org.junit.jupiter.api.Test;

public class EntityPartFilterTest {
    @Test
    void injectsNamedEntityPartUsingResourceInterfaceAnnotations() throws Exception {
        String body = """
                --upload-boundary
                Content-Disposition: form-data; name="note"
                Content-Type: text/plain; charset=UTF-8

                accompanying note
                --upload-boundary
                Content-Disposition: form-data; name="attachment"; filename="report.txt"
                Content-Type: text/plain; charset=UTF-8

                Analytical engine report
                --upload-boundary--
                """;
        ResteasyProviderFactory previous = ResteasyProviderFactory.peekInstance();
        try (ResteasyContext.CloseableContext context = ResteasyContext.addCloseableContextDataLevel()) {
            Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();
            dispatcher.getProviderFactory().registerProvider(StringTextStar.class);
            dispatcher.getProviderFactory().registerProvider(EntityPartFilter.class);
            dispatcher.getRegistry().addSingletonResource(new UploadResource());
            MockHttpRequest request = MockHttpRequest.post("/upload")
                    .contentType("multipart/form-data; boundary=upload-boundary")
                    .content(body.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
            MockHttpResponse response = new MockHttpResponse();

            // MockHttpRequest lacks the servlet transport's multipart-context lookup.
            dispatcher.invoke(new DelegatingHttpRequest(request) {
                @Override
                public Optional<EntityPart> getFormEntityPart(String name) {
                    MultipartContent content = ResteasyContext.getContextData(MultipartContent.class);
                    assertThat(content).isNotNull();
                    return content.entityPart(name);
                }
            }, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("attachment|report.txt|text/plain|Analytical engine report");
        } finally {
            ResteasyProviderFactory.setInstance(previous);
        }
    }

    public interface UploadEndpoint {
        @POST
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Produces(MediaType.TEXT_PLAIN)
        String upload(@FormParam("attachment") EntityPart attachment) throws IOException;
    }

    @Path("/upload")
    public static class UploadResource implements UploadEndpoint {
        @Override
        public String upload(EntityPart attachment) throws IOException {
            MediaType mediaType = attachment.getMediaType();
            return attachment.getName() + "|" + attachment.getFileName().orElseThrow() + "|"
                    + mediaType.getType() + "/" + mediaType.getSubtype() + "|" + attachment.getContent(String.class);
        }
    }
}
