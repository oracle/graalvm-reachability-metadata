/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_multipart_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;

import io.undertow.Undertow;

import org.jboss.resteasy.plugins.providers.multipart.EntityPartFilter;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
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
        UploadServer server = new UploadServer();
        server.getDeployment().getResources().add(new UploadResource());
        server.getDeployment().getProviderClasses().add(EntityPartFilter.class.getName());
        try {
            server.start(Undertow.builder()
                    .addHttpListener(0, "127.0.0.1")
                    .setIoThreads(1)
                    .setWorkerThreads(2));
            server.deploy();
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.boundPort() + "/upload"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "multipart/form-data; boundary=upload-boundary")
                        .POST(HttpRequest.BodyPublishers.ofString(body.replace("\n", "\r\n"), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo("attachment|report.txt|text/plain|Analytical engine report");
            }
        } finally {
            try {
                server.stop();
            } finally {
                ResteasyProviderFactory.setInstance(previous);
            }
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

    private static class UploadServer extends UndertowJaxrsServer {
        int boundPort() {
            InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
            return address.getPort();
        }
    }
}
