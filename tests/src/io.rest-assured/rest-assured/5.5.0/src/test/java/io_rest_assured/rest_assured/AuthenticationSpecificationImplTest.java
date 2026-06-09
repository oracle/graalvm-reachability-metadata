/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.security.KeyStore;

import io.restassured.authentication.AuthenticationScheme;
import io.restassured.authentication.CertAuthScheme;
import io.restassured.authentication.CertificateAuthSettings;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.authentication.CertificateAuthSettings.certAuthSettings;
import static io.restassured.specification.SpecificationQuerier.query;
import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationSpecificationImplTest {
    private static final String KEY_STORE_TYPE = "PKCS12";

    @Test
    void configuresCertificateAuthenticationFromRequestAuthenticationSpecification() {
        CertificateAuthSettings settings = certAuthSettings()
                .keyStoreType(KEY_STORE_TYPE)
                .trustStoreType(KEY_STORE_TYPE)
                .port(9443)
                .allowAllHostnames();

        RequestSpecification requestSpecification = given()
                .auth()
                .certificate("client-certificate.p12", "certificate-password", settings);

        AuthenticationScheme authenticationScheme = query(requestSpecification).getAuthenticationScheme();
        assertThat(authenticationScheme).isInstanceOf(CertAuthScheme.class);
        CertAuthScheme certAuthScheme = (CertAuthScheme) authenticationScheme;
        assertThat(certAuthScheme.getPathToTrustStore()).isEqualTo("client-certificate.p12");
        assertThat(certAuthScheme.getTrustStorePassword()).isEqualTo("certificate-password");
        assertThat(certAuthScheme.getTrustStoreType()).isEqualTo(KEY_STORE_TYPE);
        assertThat(certAuthScheme.getKeystoreType()).isEqualTo(KEY_STORE_TYPE);
        assertThat(certAuthScheme.getPort()).isEqualTo(9443);
        assertThat(certAuthScheme.getTrustStore()).isNull();
        assertThat(certAuthScheme.getKeyStore()).isNull();
        assertThat(certAuthScheme.getX509HostnameVerifier()).isNotNull();
    }

    @Test
    void configuresCertificateAuthenticationWithDefaultSettings() {
        RequestSpecification requestSpecification = given()
                .auth()
                .certificate("default-certificate.jks", "default-password");

        AuthenticationScheme authenticationScheme = query(requestSpecification).getAuthenticationScheme();
        assertThat(authenticationScheme).isInstanceOf(CertAuthScheme.class);
        CertAuthScheme certAuthScheme = (CertAuthScheme) authenticationScheme;
        assertThat(certAuthScheme.getPathToTrustStore()).isEqualTo("default-certificate.jks");
        assertThat(certAuthScheme.getTrustStorePassword()).isEqualTo("default-password");
        assertThat(certAuthScheme.getTrustStoreType()).isEqualTo(KeyStore.getDefaultType());
        assertThat(certAuthScheme.getKeystoreType()).isEqualTo(KeyStore.getDefaultType());
    }
}
