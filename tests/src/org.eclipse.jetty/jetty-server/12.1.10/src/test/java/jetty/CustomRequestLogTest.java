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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

    private static final String COMPREHENSIVE_FORMAT = String.join("|",
            "percent=%%",
            "serverHost=%a",
            "explicitServerHost=%{server}a",
            "clientHost=%{client}a",
            "localHost=%{local}a",
            "remoteHost=%{remote}a",
            "serverPort=%p",
            "explicitServerPort=%{server}p",
            "clientPort=%{client}p",
            "localPort=%{local}p",
            "remotePort=%{remote}p",
            "bytesReceived=%I",
            "bytesReceivedClf=%{CLF}I",
            "bytesSent=%O",
            "bytesSentClf=%{CLF}O",
            "bytesTransferred=%S",
            "bytesTransferredClf=%{CLF}S",
            "cookies=%C",
            "cookie=%{test}C",
            "latencyMicros=%D",
            "environment=%{PATH}e",
            "file=%f",
            "protocol=%H",
            "requestHeader=%{X-Request}i",
            "keepAlive=%k",
            "method=%m",
            "responseHeader=%{X-Test}o",
            "query=%q",
            "requestLine=%r",
            "handler=%R",
            "status=%s",
            "time=%t",
            "formattedTime=%{yyyy}t",
            "latencySeconds=%T",
            "latencyMicrosAgain=%{us}T",
            "latencyMillis=%{ms}T",
            "user=%u",
            "deferredUser=%{d}u",
            "urlPath=%U",
            "connectionStatus=%X",
            "requestTrailer=%{Request-Trailer}ti",
            "responseTrailer=%{Response-Trailer}to",
            "uri=%uri",
            "uriWithoutQuery=%{-query}uri",
            "uriWithoutPathQuery=%{-path,-query}uri",
            "uriScheme=%{scheme}uri",
            "uriAuthority=%{authority}uri",
            "uriPath=%{path}uri",
            "uriQuery=%{query}uri",
            "uriHost=%{host}uri",
            "uriPort=%{port}uri",
            "attribute=%{test.attribute}attr",
            "modified=%200m");

    @Test
    void customFormatInstallsMethodHandlesForAllSupportedTokens() {
        CapturingWriter writer = new CapturingWriter();
        CustomRequestLog requestLog = new CustomRequestLog(writer, COMPREHENSIVE_FORMAT);

        assertThat(requestLog.getFormatString()).isEqualTo(COMPREHENSIVE_FORMAT);
        assertThat(requestLog.isLogDetailRequired()).isTrue();
    }

    @Test
    void customFormatWritesRequestAndResponseValues() throws Exception {
        CapturingWriter writer = new CapturingWriter();
        CustomRequestLog requestLog = new CustomRequestLog(writer, COMPREHENSIVE_FORMAT);
        Server server = new Server(0);
        server.setRequestLog(requestLog);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                request.setAttribute("test.attribute", "attribute-value");
                request.setAttribute(CustomRequestLog.LOG_DETAIL,
                        new CustomRequestLog.LogDetail("custom-handler", "/tmp/custom-resource.txt"));
                response.setStatus(200);
                response.getHeaders().add("X-Test", "response-value");
                response.write(true, ByteBuffer.wrap("OK".getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });

        server.start();
        try {
            int port = ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
            HttpResponse<String> response = sendRequest(port);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("OK");
            assertThat(writer.awaitEntry()).isTrue();
            assertThat(writer.entries()).singleElement()
                    .satisfies(entry -> assertThat(entry)
                            .contains("percent=%")
                            .contains("cookies=test=cookie-value")
                            .contains("cookie=cookie-value")
                            .contains("file=/tmp/custom-resource.txt")
                            .contains("requestHeader=request-value")
                            .contains("method=GET")
                            .contains("responseHeader=response-value")
                            .contains("query=?name=value")
                            .contains("handler=custom-handler")
                            .contains("status=200")
                            .contains("urlPath=/logged")
                            .contains("uriPath=/logged")
                            .contains("uriQuery=?name=value")
                            .contains("attribute=attribute-value")
                            .contains("modified=GET"));
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> sendRequest(int port) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/logged?name=value"))
                .GET()
                .header("Accept", "text/plain")
                .header("Cookie", "test=cookie-value")
                .header("X-Request", "request-value")
                .timeout(Duration.ofSeconds(2))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class CapturingWriter implements RequestLog.Writer {
        private final List<String> entries = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void write(String requestEntry) {
            entries.add(requestEntry);
            latch.countDown();
        }

        private boolean awaitEntry() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

        private List<String> entries() {
            return entries;
        }
    }
}
