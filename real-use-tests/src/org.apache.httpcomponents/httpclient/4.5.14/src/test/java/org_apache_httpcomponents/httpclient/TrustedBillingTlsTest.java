/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises HttpClient's TLS trust and hostname verification subsystem to download a private billing invoice.
public class TrustedBillingTlsTest {
    // Test-only self-signed identity for billing.internal.invalid, valid from 2020 through 2119.
    private static final String PRIVATE_KEY = """
            MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgIFQFrJnBwAjZ2d6m
            bZm3UZPFKK/AGU8oO9cmeMseHyihRANCAATN8UL6Au1uyagKi/K4XxCeOl11+5d8
            2hacfqjQtOoFGY7raemCKwkO9EalkvkUVhZ85MP+WQm8QI14dySsX7yb
            """;
    private static final String CERTIFICATE = """
            MIIBgzCCASigAwIBAgICBBIwCgYIKoZIzj0EAwIwIzEhMB8GA1UEAwwYYmlsbGlu
            Zy5pbnRlcm5hbC5pbnZhbGlkMCAXDTIwMDEwMTAwMDAwMFoYDzIxMjAwMTAxMDAw
            MDAwWjAjMSEwHwYDVQQDDBhiaWxsaW5nLmludGVybmFsLmludmFsaWQwWTATBgcq
            hkjOPQIBBggqhkjOPQMBBwNCAATN8UL6Au1uyagKi/K4XxCeOl11+5d82hacfqjQ
            tOoFGY7raemCKwkO9EalkvkUVhZ85MP+WQm8QI14dySsX7ybo0owSDAjBgNVHREE
            HDAaghhiaWxsaW5nLmludGVybmFsLmludmFsaWQwDAYDVR0TAQH/BAIwADATBgNV
            HSUEDDAKBggrBgEFBQcDATAKBggqhkjOPQQDAgNJADBGAiEA4D25FtZNnwW5lGXp
            Z/ZkyFgvKyXwms4vxjfxD07hjJQCIQDj2d7fl6P+Ywd/brNF0sYleXk6+fMLgt4f
            mmtnKITJJQ==
            """;

    @Test
    @Timeout(60)
    public void trustsTheBillingCertificateWithoutDisablingHostnameVerification() throws Exception {
        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(Base64.getMimeDecoder().decode(CERTIFICATE)));
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(PRIVATE_KEY)));
        char[] password = "test-only-billing-key".toCharArray();
        KeyStore identity = KeyStore.getInstance("PKCS12");
        identity.load(null, null);
        identity.setKeyEntry("billing", privateKey, password, new Certificate[]{certificate});
        SSLContext serverTls = SSLContexts.custom().loadKeyMaterial(identity, password).build();

        // The application trusts this service's certificate, not arbitrary self-signed peers.
        KeyStore trustedBilling = KeyStore.getInstance("PKCS12");
        trustedBilling.load(null, null);
        trustedBilling.setCertificateEntry("billing", certificate);
        SSLContext clientTls = SSLContexts.custom().loadTrustMaterial(trustedBilling, null).build();
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String invoice = """
                invoice,customer,total
                INV-1042,Acme Supplies,125.50
                """;
        List<String> receivedRequests = new CopyOnWriteArrayList<>();
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        HttpsServer server = HttpsServer.create(new InetSocketAddress(loopback, 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        server.createContext("/invoices/INV-1042", exchange -> {
            try (exchange) {
                receivedRequests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                byte[] body = invoice.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        try (CloseableHttpClient client = HttpClients.custom()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(clientTls))
                // Both logical service names reach the local fixture; TLS must still check the name.
                .setDnsResolver(hostname -> new InetAddress[]{loopback})
                .setDefaultRequestConfig(config)
                .disableAutomaticRetries()
                .build()) {
            String wrongHost = "https://other.internal.invalid:" + port + "/invoices/INV-1042";
            assertThrows(SSLPeerUnverifiedException.class,
                    () -> client.execute(new HttpGet(wrongHost), new BasicResponseHandler()));
            assertEquals(List.of(), receivedRequests, "No HTTP request may reach an incorrectly named peer");

            String invoiceUrl = "https://billing.internal.invalid:" + port + "/invoices/INV-1042";
            try (CloseableHttpResponse response = client.execute(new HttpGet(invoiceUrl))) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertEquals(invoice, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }
            assertEquals(List.of("GET /invoices/INV-1042"), receivedRequests);
        } finally {
            server.stop(0);
        }
    }
}
