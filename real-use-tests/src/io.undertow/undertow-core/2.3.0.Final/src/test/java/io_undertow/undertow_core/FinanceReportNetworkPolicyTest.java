/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ForwardedHandler;
import io.undertow.server.handlers.IPAddressAccessControlHandler;
import io.undertow.util.Headers;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * IP address access control with `IPAddressAccessControlHandler` protects a finance report.
 *
 * The backend listener is private to a trusted edge proxy that replaces incoming Forwarded
 * headers with verified client addresses. Requests here represent that proxy's backend traffic,
 * not untrusted Internet requests. `ForwardedHandler` restores the client address before the
 * network policy runs. Corporate IPv4 and IPv6 networks may download the report, except for a
 * quarantined workstation whose deny rule precedes the corporate allow rule. All other addresses,
 * including backend calls without an identified client, are denied by default.
 */
@Timeout(60)
public class FinanceReportNetworkPolicyTest {

    private static final String REPORT = """
            department,total
            engineering,4200
            operations,1800
            """;

    private Undertow server;
    private HttpClient client;
    private URI report;

    @BeforeEach
    void startReportingService() {
        IPAddressAccessControlHandler policy = new IPAddressAccessControlHandler(
                FinanceReportNetworkPolicyTest::downloadReport)
                .setDefaultAllow(false)
                .addDeny("192.0.2.99")
                .addAllow("192.0.2.0/24")
                .addAllow("2001:0db8:0042:0000:0000:0000:0000:0000/48");
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new ForwardedHandler(policy))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        report = URI.create("http://127.0.0.1:" + address.getPort() + "/finance/monthly.csv");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopReportingService() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void servesCorporateClientsButNeverLeaksReportsToDeniedAddresses() throws Exception {
        HttpResponse<String> corporate = download("for=192.0.2.41;proto=https");
        assertReport(corporate);

        HttpResponse<String> quarantined = download("for=192.0.2.99;proto=https");
        assertDenied(quarantined);

        HttpResponse<String> outsider = download("for=198.51.100.23;proto=https");
        assertDenied(outsider);

        HttpResponse<String> unidentified = download(null);
        assertDenied(unidentified);

        HttpResponse<String> corporateIpv6 = download("for=\"[2001:db8:42::7]:51000\";proto=https");
        assertReport(corporateIpv6);

        HttpResponse<String> outsideIpv6 = download("for=\"[2001:db8:43::7]:51000\";proto=https");
        assertDenied(outsideIpv6);

        assertReport(download("for=192.0.2.42;proto=https"));
    }

    private static void downloadReport(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION, "attachment; filename=monthly.csv");
        exchange.getResponseSender().send(REPORT);
    }

    private HttpResponse<String> download(String forwarded) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(report).timeout(Duration.ofSeconds(10));
        if (forwarded != null) {
            request.header("Forwarded", forwarded);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertReport(HttpResponse<String> response) {
        assertEquals(200, response.statusCode());
        assertEquals(REPORT, response.body());
        assertEquals("text/csv; charset=UTF-8",
                response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("attachment; filename=monthly.csv",
                response.headers().firstValue("Content-Disposition").orElseThrow());
    }

    private static void assertDenied(HttpResponse<String> response) {
        assertEquals(403, response.statusCode());
        assertFalse(response.body().contains("engineering"));
        assertFalse(response.headers().firstValue("Content-Disposition").isPresent());
    }
}
