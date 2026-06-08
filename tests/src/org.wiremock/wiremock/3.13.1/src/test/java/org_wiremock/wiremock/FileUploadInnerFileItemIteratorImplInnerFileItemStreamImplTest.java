/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Request;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class FileUploadInnerFileItemIteratorImplInnerFileItemStreamImplTest {
    @Test
    void multipartRequestPartsAreParsedFromTheWireMockRequestBody() throws Exception {
        String boundary = "wiremock-test-boundary";
        String multipartBody = String.join("\r\n",
                "--" + boundary,
                "Content-Disposition: form-data; name=\"metadata\"",
                "",
                "upload metadata",
                "--" + boundary,
                "Content-Disposition: form-data; name=\"file\"; filename=\"sample.txt\"",
                "Content-Type: text/plain",
                "",
                "file contents",
                "--" + boundary + "--",
                "");
        AtomicReference<Collection<Request.Part>> observedParts = new AtomicReference<>();
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.addMockServiceRequestListener(
                (request, response) -> observedParts.set(request.getParts()));

        try {
            server.start();
            server.stubFor(post(urlEqualTo("/upload")).willReturn(aResponse().withStatus(201)));

            int responseCode = postMultipartRequest(server.baseUrl(), boundary, multipartBody);

            assertThat(responseCode).isEqualTo(201);
            Collection<Request.Part> parts = observedParts.get();
            assertThat(parts).isNotNull();
            assertThat(parts)
                    .extracting(Request.Part::getName)
                    .containsExactlyInAnyOrder("metadata", "file");

            Request.Part filePart = parts.stream()
                    .filter(part -> "file".equals(part.getName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(filePart.getFileName()).isEqualTo("sample.txt");
            assertThat(filePart.getHeader("Content-Type").firstValue()).isEqualTo("text/plain");
            assertThat(filePart.getBody().asString()).isEqualTo("file contents");
        } finally {
            server.stop();
        }
    }

    private static int postMultipartRequest(
            String baseUrl, String boundary, String body) throws Exception {
        byte[] bodyBytes = body.getBytes(UTF_8);
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + "/upload")
                .toURL()
                .openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(bodyBytes.length);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bodyBytes);
        }

        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }
}
