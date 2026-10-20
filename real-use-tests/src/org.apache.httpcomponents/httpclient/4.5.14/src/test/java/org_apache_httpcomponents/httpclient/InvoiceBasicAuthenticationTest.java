/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's HTTP Basic authentication subsystem to download a protected invoice.
public class InvoiceBasicAuthenticationTest {
    @Test
    @Timeout(60)
    public void downloadsInvoiceAfterAnsweringTheBillingRealmChallenge() throws IOException {
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                "invoice-reader:local-secret".getBytes(StandardCharsets.US_ASCII));
        String invoice = """
                invoice,customer,total
                INV-1042,Acme Supplies,125.50
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/billing/invoices/INV-1042", exchange -> {
            if (!expectedAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"billing\"");
                respond(exchange, 401, "Billing credentials required");
                return;
            }
            respond(exchange, 200, invoice);
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        int port = server.getAddress().getPort();
        String invoiceUrl = "http://127.0.0.1:" + port + "/billing/invoices/INV-1042";
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet(invoiceUrl))) {
                assertEquals(401, response.getStatusLine().getStatusCode());
                assertEquals("Basic realm=\"billing\"", response.getFirstHeader("WWW-Authenticate").getValue());
                assertEquals("Billing credentials required",
                        EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }

            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(new AuthScope("127.0.0.1", port, "billing", "Basic"),
                    new UsernamePasswordCredentials("invoice-reader", "local-secret"));
            HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentials);

            String downloadedInvoice = client.execute(new HttpGet(invoiceUrl), new BasicResponseHandler(), context);
            assertEquals(invoice, downloadedInvoice);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
