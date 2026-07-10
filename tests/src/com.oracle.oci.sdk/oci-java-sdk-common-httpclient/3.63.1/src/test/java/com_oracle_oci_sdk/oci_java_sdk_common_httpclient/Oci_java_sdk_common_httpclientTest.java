/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import com.oracle.bmc.http.client.ClientProperty;
import com.oracle.bmc.http.client.KeyStoreWithPassword;
import com.oracle.bmc.http.client.Options;
import com.oracle.bmc.http.client.ProxyConfiguration;
import com.oracle.bmc.http.client.StandardClientProperties;
import com.oracle.bmc.http.client.pki.Pem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_common_httpclientTest {
    @Test
    void pemEncodesAndDecodesDefaultAndLegacyRsaKeys() throws Exception {
        KeyPair keyPair = createRsaKeyPair();

        String defaultPublicKey = Pem.encoder().encode(keyPair.getPublic());
        byte[] defaultPrivateKey = Pem.encoder().encode(keyPair.getPrivate());
        String legacyPublicKey = Pem.encoder().with(Pem.Format.LEGACY).encode(keyPair.getPublic());
        byte[] legacyPrivateKey =
                Pem.encoder().with(Pem.Format.LEGACY).encode(keyPair.getPrivate());
        try {
            assertThat(defaultPublicKey).contains("BEGIN PUBLIC KEY");
            assertThat(defaultPrivateKey)
                    .startsWith("-----BEGIN PRIVATE KEY-----".getBytes(StandardCharsets.UTF_8));
            assertThat(legacyPublicKey).contains("BEGIN RSA PUBLIC KEY");
            assertThat(legacyPrivateKey)
                    .startsWith("-----BEGIN RSA PRIVATE KEY-----".getBytes(StandardCharsets.UTF_8));

            assertThat(Pem.decoder().decodePublicKey(defaultPublicKey).getEncoded())
                    .isEqualTo(keyPair.getPublic().getEncoded());
            assertThat(Pem.decoder()
                            .decodePublicKey(
                                    Channels.newChannel(
                                            new ByteArrayInputStream(
                                                    legacyPublicKey.getBytes(StandardCharsets.UTF_8))))
                            .getEncoded())
                    .isEqualTo(keyPair.getPublic().getEncoded());
            assertThat(Pem.decoder().decodePrivateKey(defaultPrivateKey).getEncoded())
                    .isEqualTo(keyPair.getPrivate().getEncoded());
            assertThat(Pem.decoder()
                            .decodePrivateKey(
                                    Channels.newChannel(new ByteArrayInputStream(legacyPrivateKey)))
                            .getEncoded())
                    .isEqualTo(keyPair.getPrivate().getEncoded());
        } finally {
            Arrays.fill(defaultPrivateKey, (byte) 0);
            Arrays.fill(legacyPrivateKey, (byte) 0);
        }
    }

    @Test
    void pemEncryptsAndDecodesLegacyPrivateKeys() throws Exception {
        KeyPair keyPair = createRsaKeyPair();
        char[] encryptionPassword = "encryption-password".toCharArray();
        byte[] initializationVector = new byte[16];
        Arrays.fill(initializationVector, (byte) 7);

        Pem.Encryption encryption =
                Pem.Encryption.builder()
                        .iv(initializationVector)
                        .passphrase(encryptionPassword)
                        .build();
        byte[] encryptedPrivateKey = null;
        try {
            encryptedPrivateKey =
                    Pem.encoder()
                            .with(Pem.Format.LEGACY)
                            .with(encryption)
                            .encode(keyPair.getPrivate());
            assertThat(encryptedPrivateKey)
                    .startsWith("-----BEGIN RSA PRIVATE KEY-----".getBytes(StandardCharsets.UTF_8))
                    .contains("Proc-Type: 4,ENCRYPTED".getBytes(StandardCharsets.UTF_8));

            try (Pem.Passphrase decodingPassword =
                    Pem.Passphrase.of("encryption-password".toCharArray())) {
                assertThat(
                                Pem.decoder()
                                        .with(decodingPassword)
                                        .decodePrivateKey(encryptedPrivateKey)
                                        .getEncoded())
                        .isEqualTo(keyPair.getPrivate().getEncoded());
            }
        } finally {
            if (encryptedPrivateKey != null) {
                Arrays.fill(encryptedPrivateKey, (byte) 0);
            }
            encryption.close();
            Arrays.fill(encryptionPassword, '\0');
        }
    }

    @Test
    void pemWritesDefaultPrivateKeysToWritableChannels() throws Exception {
        KeyPair keyPair = createRsaKeyPair();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] encodedPrivateKey = null;

        try (WritableByteChannel channel = Channels.newChannel(output)) {
            assertThat(Pem.encoder().write(channel, keyPair.getPrivate())).isSameAs(channel);
            encodedPrivateKey = output.toByteArray();

            assertThat(encodedPrivateKey)
                    .startsWith("-----BEGIN PRIVATE KEY-----".getBytes(StandardCharsets.UTF_8));
            assertThat(Pem.decoder().decodePrivateKey(encodedPrivateKey).getEncoded())
                    .isEqualTo(keyPair.getPrivate().getEncoded());
        } finally {
            if (encodedPrivateKey != null) {
                Arrays.fill(encodedPrivateKey, (byte) 0);
            }
        }
    }

    @Test
    void exposesProxyPropertiesAndConfigurableResponseStreamBehavior() throws Exception {
        char[] password = "proxy-password".toCharArray();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8080));
        ProxyConfiguration configuration =
                ProxyConfiguration.builder().proxy(proxy).username("user").password(password).build();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        KeyStoreWithPassword keyStoreWithPassword =
                new KeyStoreWithPassword(keyStore, "store-password");
        boolean originalAutoClose = Options.getShouldAutoCloseResponseInputStream();

        try {
            assertThat(configuration.getProxy()).isSameAs(proxy);
            assertThat(configuration.getUsername()).isEqualTo("user");
            assertThat(configuration.getPassword()).isSameAs(password);
            assertThat(keyStoreWithPassword.getKeyStore()).isSameAs(keyStore);
            assertThat(keyStoreWithPassword.getPassword()).isEqualTo("store-password");
            assertThat(StandardClientProperties.CONNECT_TIMEOUT.getName()).isEqualTo("connectTimeout");
            assertThat(StandardClientProperties.PROXY.toString()).isEqualTo("proxy");

            ClientProperty<String> property = ClientProperty.create("custom-property");
            assertThat(property.getName()).isEqualTo("custom-property");
            assertThat(property).hasToString("custom-property");

            Options.shouldAutoCloseResponseInputStream(!originalAutoClose);
            assertThat(Options.getShouldAutoCloseResponseInputStream()).isEqualTo(!originalAutoClose);
        } finally {
            Options.shouldAutoCloseResponseInputStream(originalAutoClose);
        }

    }

    @Test
    void pemEncodesAndDecodesCertificatesAndCertificateChains() throws Exception {
        String certificatePem =
                """
                -----BEGIN CERTIFICATE-----
                MIIDCTCCAfGgAwIBAgIUGfXfFJsWLOD2egJjcy3vW9atBKswDQYJKoZIhvcNAQEL
                BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDcxMDEzMTkwNVoXDTI2MDcx
                MTEzMTkwNVowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
                AAOCAQ8AMIIBCgKCAQEAtQui6U3xGzuKBhDi1HLMJRbvrlAgO6wpRVUqMFZ17o8Q
                v4nGqReSuqYkWAR9dkzg1wgq8WK6+s0+eOnQaNEcCHLp5yBCBFLRuDJLBvhcJtZJ
                gC3VE8sWW5KwIWUCwBk5RqCLhOS9O4Ws3vg4ITSsK3wvmBocrd993fjOSJfINMRH
                qi9ygv5JdAFKDfl+1VshQ5LoJDvKsBU23vs4CN2/woF2YwiPoGQQcWCZmaqA5u5A
                7qQT9nE2+S6WfnHVQRidd4ooUedZggdERkceQCUDOml3wilcMFPvWTKMpEq9Mv7X
                2Ypw1OcDapbou9/RRotDTxBsT36YlCIiRZ+lieJ/ZQIDAQABo1MwUTAdBgNVHQ4E
                FgQUM2Ic4kTNtwpGx+V/57KpN9bN6ZwwHwYDVR0jBBgwFoAUM2Ic4kTNtwpGx+V/
                57KpN9bN6ZwwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAdR61
                mz8tp5YJfYQZu91PNrtD5IZNQElUg5PgCI+DGWAZc4Fc7s4VEGlA/Hzose98Ngj5
                Yv2emKjxsa0Ojs5r0VQ+NzR10txc9mjHRBskfJgLCAOJbB44OPHGFM3W6j6sOx2B
                /rce/EKuKY4eBQoZ1KPM87nIAYqY+0wxEEJGkRImXa1U0YKac0z17eSwWR/Wpj3b
                hIpLOSPIVvVjc+TW8IIMZDAPjRg4Hn7ahC+YSAAmA6pWK9AHUzP0eocSESSvyrMV
                UzsySZwM989m7CtZyvLrmC9AlZqNHaMkIzEOZwNBa2D2DUhQhhA5duSqQslCNb43
                v+/3DMprIoULBoNqrQ==
                -----END CERTIFICATE-----
                """;

        Certificate certificate = Pem.decoder().decodeCertificate(certificatePem);
        String encodedCertificate = Pem.encoder().encode(certificate);
        Collection<? extends Certificate> certificateChain =
                Pem.decoder().decodeCertificateChain(encodedCertificate + "\n" + encodedCertificate);

        assertThat(encodedCertificate).contains("BEGIN CERTIFICATE");
        assertThat(Pem.decoder().decodeCertificate(encodedCertificate).getEncoded())
                .isEqualTo(certificate.getEncoded());
        assertThat(certificateChain).hasSize(2);
        Collection<? extends Certificate> encodedAndDecodedCertificateChain =
                Pem.decoder().decodeCertificateChain(Pem.encoder().encode(certificateChain));
        assertThat(encodedAndDecodedCertificateChain)
                .hasSize(2)
                .allSatisfy(
                        decodedCertificate ->
                                assertThat(decodedCertificate.getEncoded())
                                        .isEqualTo(certificate.getEncoded()));
    }

    private KeyPair createRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
