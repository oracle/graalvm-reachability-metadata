/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp.okhttp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

final class TlsTestSupport {
    private TlsTestSupport() {
    }

    static SSLContext context() throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore keyStore = keyStore();
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, password);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    static Certificate certificate() throws Exception {
        return keyStore().getCertificate("localhost");
    }

    static String serveRequest(SSLServerSocket server) throws Exception {
        try (SSLSocket socket = (SSLSocket) server.accept()) {
            socket.setSoTimeout(10_000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String firstLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Consume the request headers before sending the response.
            }
            socket.getOutputStream().write("""
                    HTTP/1.1 200 OK\r
                    Content-Length: 6\r
                    Connection: close\r
                    \r
                    secure""".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return firstLine;
        }
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = TlsTestSupport.class.getResourceAsStream("localhost.p12")) {
            if (input == null) {
                throw new IllegalStateException("Missing loopback TLS certificate");
            }
            keyStore.load(input, "changeit".toCharArray());
        }
        return keyStore;
    }
}
