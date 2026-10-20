/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_transports_http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.namespace.QName;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.junit.jupiter.api.Test;

public class HTTPConduitInnerWrappedOutputStreamTest {
    private static final int IO_TIMEOUT_MILLIS = 10_000;

    @Test
    void exchangesAChunkedRequestAndMapsConnectionFailures() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", new EchoHandler(requestBody));
        server.start();
        Bus bus = BusFactory.newInstance().createBus();

        try {
            String address =
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            AtomicReference<String> responseBody = new AtomicReference<String>();
            HTTPConduit conduit = newConduit(bus, address);
            conduit.setMessageObserver(new ResponseObserver(responseBody));
            Message request = new MessageImpl();
            request.put(Message.HTTP_REQUEST_METHOD, "POST");

            conduit.prepare(request);
            OutputStream output = request.getContent(OutputStream.class);
            output.write("request-body".getBytes(StandardCharsets.UTF_8));
            output.close();

            assertThat(requestBody.get()).isEqualTo("request-body");
            assertThat(responseBody.get()).isEqualTo("response-body");

            HttpServer unavailableServer =
                    HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int unavailablePort = unavailableServer.getAddress().getPort();
            unavailableServer.start();
            unavailableServer.stop(0);
            HTTPConduit unavailableConduit =
                    newConduit(bus, "http://127.0.0.1:" + unavailablePort + "/unavailable");
            unavailableConduit.setMessageObserver(new ResponseObserver(new AtomicReference<String>()));
            Message unavailableRequest = new MessageImpl();
            unavailableRequest.put(Message.HTTP_REQUEST_METHOD, "POST");
            unavailableConduit.prepare(unavailableRequest);

            assertThatThrownBy(() -> unavailableRequest.getContent(OutputStream.class).close())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("invoking http://127.0.0.1:")
                    .hasCauseInstanceOf(IOException.class);
            unavailableConduit.close();
            conduit.close();
        } finally {
            server.stop(0);
            bus.shutdown(true);
        }
    }

    private static HTTPConduit newConduit(Bus bus, String address) throws IOException {
        EndpointInfo endpoint = new EndpointInfo();
        endpoint.setName(new QName("urn:test", "endpoint"));
        endpoint.setAddress(address);
        HTTPConduit conduit = new HTTPConduit(bus, endpoint);
        HTTPClientPolicy client = new HTTPClientPolicy();
        client.setConnectionTimeout(IO_TIMEOUT_MILLIS);
        client.setReceiveTimeout(IO_TIMEOUT_MILLIS);
        client.setAllowChunking(true);
        client.setChunkingThreshold(1);
        conduit.setClient(client);
        conduit.setAuthorization(new AuthorizationPolicy());
        conduit.setProxyAuthorization(new ProxyAuthorizationPolicy());
        return conduit;
    }

    private static final class EchoHandler implements HttpHandler {
        private final AtomicReference<String> requestBody;

        private EchoHandler(AtomicReference<String> requestBody) {
            this.requestBody = requestBody;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestBody.set(readString(exchange.getRequestBody()));
            byte[] response = "response-body".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }

    private static final class ResponseObserver implements MessageObserver {
        private final AtomicReference<String> responseBody;

        private ResponseObserver(AtomicReference<String> responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public void onMessage(Message message) {
            try {
                responseBody.set(readString(message.getContent(InputStream.class)));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static String readString(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
