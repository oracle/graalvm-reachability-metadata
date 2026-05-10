/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.config.SSLConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SSLConfigTest {
    private static final String PASSWORD = "changeit";

    @Test
    void relaxedHttpsValidationLoadsKeyStoreFromContextClassLoaderResource() {
        SSLConfig sslConfig = new SSLConfig()
                .keyStore("io/restassured/config/SSLConfig.class", PASSWORD);

        assertThrows(IOException.class, sslConfig::relaxedHTTPSValidation);
    }

    @Test
    void relaxedHttpsValidationFallsBackToClassResourceWhenContextClassLoaderResourceIsMissing() {
        SSLConfig sslConfig = new SSLConfig()
                .keyStore("/io/restassured/config/SSLConfig.class", PASSWORD);

        assertThrows(IOException.class, sslConfig::relaxedHTTPSValidation);
    }
}
