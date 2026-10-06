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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomRequestLogTest {

    private static final String ALL_FORMATS = "%%|%{server}a|%{server}p|%I|%{CLF}I|%O|%{CLF}O|%S|%{CLF}S|"
            + "%C|%{session}C|%D|%{PATH}e|%f|%H|%{X-Test}i|%k|%m|%{Content-Type}o|%q|%r|%R|%s|%t|%T|"
            + "%{ms}T|%u|%{d}u|%U|%X|"
            + "%{X-Trailer}ti|%{X-Trailer}to|%uri|%{-query}uri|%{-path,-query}uri|%{scheme}uri|%{authority}uri|"
            + "%{path}uri|%{query}uri|%{host}uri|%{port}uri|%{test.attr}attr|%200s|%!404s";

    @Test
    void logsRequestThroughEveryPublicFormatCode() throws Exception {
        CapturingWriter writer = new CapturingWriter();
        CustomRequestLog requestLog = new CustomRequestLog(writer, ALL_FORMATS);
        Server server = new Server(0);
        ContextHandler context = new ContextHandler("/");
        context.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                request.setAttribute("test.attr", "attribute-value");
                response.setStatus(200);
                response.getHeaders().add("Content-Type", "text/plain");
                response.write(true, ByteBuffer.wrap("logged response".getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });
        server.setHandler(context);
        server.setRequestLog(requestLog);
        assertThat(requestLog.isLogDetailRequired()).isTrue();
        assertThat(CustomRequestLog.isLogDetailRequired(server)).isTrue();

        server.start();
        try {
            URI requestUri = server.getURI().resolve("log?name=jetty");
            HttpResponse<String> response = doHttpRequest(requestUri,
                    "X-Test", "request-value", "Cookie", "user=jetty");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("logged response");
        } finally {
            server.stop();
        }

        assertThat(writer.entries).hasSize(1);
        String entry = writer.entries.get(0);
        assertThat(entry).contains("GET", "/log?name=jetty", "request-value", "attribute-value", "200");
    }

    private static HttpResponse<String> doHttpRequest(URI uri, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "text/plain")
                .timeout(Duration.ofSeconds(10));
        if (headers.length > 0)
            request.headers(headers);

        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static final class CapturingWriter implements RequestLog.Writer {
        private final List<String> entries = new ArrayList<>();

        @Override
        public void write(String requestEntry) {
            entries.add(requestEntry);
        }
    }
}
