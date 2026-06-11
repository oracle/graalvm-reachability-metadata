/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomRequestLogTest {
    private static final String LOG_FORMAT = String.join(" ",
            "%%",
            "%{server}a", "%{client}a", "%{local}a", "%{remote}a",
            "%{server}p", "%{client}p", "%{local}p", "%{remote}p",
            "%I", "%{CLF}I", "%O", "%{CLF}O", "%S", "%{CLF}S",
            "%C", "%{sample}C", "%D", "%{PATH}e", "%f", "%H",
            "%{X-Test}i", "%k", "%m", "%{X-Response}o", "%q", "%r", "%R", "%s",
            "%t", "%{us}T", "%{ms}T", "%{s}T", "%u", "%{d}u", "%U", "%X",
            "%{X-Trailer}ti", "%{X-Response-Trailer}to",
            "%uri", "%{-query}uri", "%{-path,-query}uri", "%{scheme}uri", "%{authority}uri",
            "%{path}uri", "%{query}uri", "%{host}uri", "%{port}uri", "%{sampleAttribute}attr",
            "%200s", "%!404s");

    @Test
    void logsRequestWithAllPublicFormatTokens() throws Exception {
        CapturingRequestLogWriter writer = new CapturingRequestLogWriter();
        CustomRequestLog requestLog = new CustomRequestLog(writer, LOG_FORMAT);
        assertThat(requestLog.isLogDetailRequired()).isTrue();

        Server server = new Server(0);
        server.setRequestLog(requestLog);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                request.setAttribute("sampleAttribute", "attribute-value");
                response.setStatus(200);
                response.getHeaders().add("X-Response", "response-header-value");
                response.write(true, ByteBuffer.wrap("logged".getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });

        server.start();
        try {
            HttpResponse<String> response = doHttpRequest(server, "/hello?name=jetty");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("logged");

            String logEntry = writer.pollEntry();
            assertThat(logEntry)
                    .contains("GET")
                    .contains("/hello?name=jetty")
                    .contains("request-header-value")
                    .contains("response-header-value")
                    .contains("sample=cookie-value")
                    .contains("cookie-value")
                    .contains("attribute-value");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(Server server, String path)
            throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            HttpRequest request = HttpRequest.newBuilder(localUri(server, path))
                    .GET()
                    .header("Accept", "text/plain")
                    .header("Cookie", "sample=cookie-value; other=other-value")
                    .header("X-Test", "request-header-value")
                    .timeout(Duration.ofSeconds(2))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static URI localUri(Server server, String path) {
        NetworkConnector connector = (NetworkConnector) server.getConnectors()[0];
        return URI.create("http://localhost:" + connector.getLocalPort() + path);
    }

    private static class CapturingRequestLogWriter implements RequestLog.Writer {
        private final BlockingQueue<String> entries = new LinkedBlockingQueue<>();

        @Override
        public void write(String requestEntry) {
            entries.add(requestEntry);
        }

        private String pollEntry() throws InterruptedException {
            String requestEntry = entries.poll(5, TimeUnit.SECONDS);
            assertThat(requestEntry).isNotNull();
            return requestEntry;
        }
    }
}
