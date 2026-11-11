/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(90)
public class DohResolverTest {
    @Test
    void resolvesAddressWithGet() throws Exception {
        exerciseExchange(false);
    }

    @Test
    void resolvesAddressWithPost() throws Exception {
        exerciseExchange(true);
    }

    private void exerciseExchange(boolean usePost) throws Exception {
        Name name = Name.fromString("service.example.");
        InetAddress address = InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 42});
        ARecord answer = new ARecord(name, DClass.IN, 300, address);
        CompletableFuture<ReceivedRequest> received = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dns-query", exchange -> {
            try (exchange) {
                byte[] wire;
                if ("POST".equals(exchange.getRequestMethod())) {
                    wire = exchange.getRequestBody().readAllBytes();
                } else {
                    String encodedQuery = exchange.getRequestURI().getRawQuery().substring("dns=".length());
                    wire = Base64.getUrlDecoder().decode(encodedQuery);
                }
                Message request = new Message(wire);
                Message response = new Message(request.getHeader().getID());
                response.getHeader().setFlag(Flags.QR);
                response.getHeader().setFlag(Flags.RA);
                response.addRecord(request.getQuestion(), Section.QUESTION);
                response.addRecord(answer, Section.ANSWER);
                byte[] responseBytes = response.toWire();
                exchange.getResponseHeaders().set("Content-Type", "application/dns-message");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                received.complete(new ReceivedRequest(exchange.getRequestMethod(),
                        exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders(), request));
            } catch (IOException | RuntimeException e) {
                received.completeExceptionally(e);
                throw e;
            }
        });
        server.start();
        try {
            DohResolver resolver = new DohResolver("http://127.0.0.1:" + server.getAddress().getPort() + "/dns-query");
            resolver.setTimeout(Duration.ofSeconds(30));
            resolver.setUsePost(usePost);
            Message query = Message.newQuery(Record.newRecord(name, Type.A, DClass.IN));
            query.getHeader().setID(1234);

            Message response = resolver.sendAsync(query).toCompletableFuture().get(30, TimeUnit.SECONDS);
            ReceivedRequest request = received.get(30, TimeUnit.SECONDS);

            assertThat(response.getRcode()).isEqualTo(Rcode.NOERROR);
            assertThat(response.getHeader().getFlag(Flags.QR)).isTrue();
            assertThat(response.getQuestion()).isEqualTo(query.getQuestion());
            assertThat(response.getSection(Section.ANSWER)).containsExactly(answer);
            assertThat(((ARecord) response.getSection(Section.ANSWER).getFirst()).getAddress()).isEqualTo(address);
            assertThat(response.getResolver()).contains(resolver);
            assertThat(request.method()).isEqualTo(usePost ? "POST" : "GET");
            assertThat(request.headers().getFirst("Accept")).isEqualTo("application/dns-message");
            assertThat(request.headers().getFirst("Content-Type")).isEqualTo("application/dns-message");
            // Require the modern client's HTTP/2 negotiation, not the legacy URLConnection fallback.
            assertThat(request.headers().getFirst("Upgrade")).isEqualToIgnoringCase("h2c");
            assertThat(request.message().getQuestion()).isEqualTo(query.getQuestion());
            assertThat(request.message().getHeader().getID()).isZero();
            assertThat(request.message().getOPT()).isNotNull();
            if (usePost) {
                assertThat(request.rawQuery()).isNull();
            } else {
                assertThat(request.rawQuery()).startsWith("dns=");
            }
            assertThat(query.getHeader().getID()).isEqualTo(1234);
            assertThat(query.getOPT()).isNull();
        } finally {
            server.stop(0);
        }
    }

    private record ReceivedRequest(String method, String rawQuery, Headers headers, Message message) {
    }
}
