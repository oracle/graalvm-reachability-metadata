/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_http2.http2_server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BytesRequestContent;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.server.AbstractHTTP2ServerConnectionFactory.HTTP2SessionContainer;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class HTTP2ServerConnectionFactoryTest {
    private final Server server = new Server();
    private final HTTP2ServerConnectionFactory factory = new HTTP2ServerConnectionFactory(new HttpConfiguration());
    private final HttpClient client = new HttpClient(new HttpClientTransportOverHTTP2(new HTTP2Client()));
    private ServerConnector connector;

    @BeforeEach
    void startServerAndClient() throws Exception {
        factory.setInitialStreamRecvWindow(16384);
        factory.setMaxConcurrentStreams(16);
        connector = new ServerConnector(server, factory);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(30000);
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.setHeader("X-Request-Protocol", request.getProtocol());
                if ("/echo".equals(target)) {
                    byte[] body = request.getInputStream().readAllBytes();
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/octet-stream");
                    response.setContentLength(body.length);
                    response.getOutputStream().write(body);
                } else if ("/greeting".equals(target)) {
                    byte[] body = "hello over HTTP/2".getBytes(StandardCharsets.UTF_8);
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentLength(body.length);
                    response.getOutputStream().write(body);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No such endpoint");
                }
                baseRequest.setHandled(true);
            }
        });
        client.setConnectTimeout(30000);
        client.setIdleTimeout(30000);
        server.start();
        client.start();
    }

    @AfterEach
    void stopServerAndClient() throws Exception {
        try {
            client.stop();
        } finally {
            server.stop();
        }
    }

    @Test
    void echoesRequestDataAcrossFlowControlWindows() throws Exception {
        byte[] payload = "flow-controlled-payload\n".repeat(8192).getBytes(StandardCharsets.UTF_8);
        ContentResponse response = client.POST(uri("/echo"))
                .body(new BytesRequestContent("application/octet-stream", payload))
                .timeout(30, TimeUnit.SECONDS).send();
        assertThat(response.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("X-Request-Protocol")).isEqualTo("HTTP/2.0");
        assertThat(response.getContent()).isEqualTo(payload);

        ContentResponse next = client.newRequest(uri("/greeting")).timeout(30, TimeUnit.SECONDS).send();
        assertThat(next.getStatus()).isEqualTo(200);
        assertThat(next.getContentAsString()).isEqualTo("hello over HTTP/2");
        HTTP2SessionContainer sessions = factory.getBean(HTTP2SessionContainer.class);
        assertThat(sessions.getSessions()).hasSize(1);
        assertThat(sessions.getSize()).isEqualTo(1);
    }

    @Test
    void headReturnsHeadersWithoutResponseData() throws Exception {
        ContentResponse response = client.newRequest(uri("/greeting")).method("HEAD").timeout(30, TimeUnit.SECONDS).send();
        assertThat(response.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Content-Length")).isEqualTo("17");
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void sendsErrorResponsesWithoutClosingTheSession() throws Exception {
        ContentResponse missing = client.newRequest(uri("/missing")).timeout(30, TimeUnit.SECONDS).send();
        assertThat(missing.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(missing.getStatus()).isEqualTo(404);
        ContentResponse greeting = client.newRequest(uri("/greeting")).timeout(30, TimeUnit.SECONDS).send();
        assertThat(greeting.getStatus()).isEqualTo(200);
        assertThat(greeting.getContentAsString()).isEqualTo("hello over HTTP/2");
    }

    private String uri(String path) {
        return "http://127.0.0.1:" + connector.getLocalPort() + path;
    }
}
