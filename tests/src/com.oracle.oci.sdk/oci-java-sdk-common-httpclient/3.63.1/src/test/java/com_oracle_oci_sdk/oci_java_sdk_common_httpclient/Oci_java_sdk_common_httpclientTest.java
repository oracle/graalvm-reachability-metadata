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
import com.oracle.bmc.http.client.ProxyConfiguration;
import com.oracle.bmc.http.client.StandardClientProperties;
import com.oracle.bmc.http.client.pki.Pem;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;

public class Oci_java_sdk_common_httpclientTest {
    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
    }

    @Test
    void clientConfigurationObjectsExposeTypedProperties() throws Exception {
        InetSocketAddress proxyAddress = InetSocketAddress.createUnresolved("proxy.example.com", 8080);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyAddress);
        char[] proxyPassword = "proxy-password".toCharArray();

        ProxyConfiguration proxyConfiguration = ProxyConfiguration.builder()
                .proxy(proxy)
                .username("proxy-user")
                .password(proxyPassword)
                .build();

        assertThat(proxyConfiguration.getProxy()).isSameAs(proxy);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("proxy-user");
        assertThat(proxyConfiguration.getPassword()).containsExactly(proxyPassword);
        assertThat(StandardClientProperties.PROXY.toString())
                .isEqualTo(StandardClientProperties.PROXY.getName());

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        KeyStoreWithPassword keyStoreWithPassword = new KeyStoreWithPassword(
                keyStore,
                "keystore-password");

        assertThat(keyStoreWithPassword.getKeyStore()).isSameAs(keyStore);
        assertThat(keyStoreWithPassword.getPassword()).isEqualTo("keystore-password");
        assertThat(StandardClientProperties.KEY_STORE.toString())
                .isEqualTo(StandardClientProperties.KEY_STORE.getName());

        ClientProperty<String> customProperty = ClientProperty.create("custom-property");
        assertThat(customProperty.getName()).isEqualTo("custom-property");
        assertThat(customProperty).hasToString("custom-property");
    }

    @Test
    void pemEncoderAndDecoderRoundTripPublicKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        String pem = Pem.encoder().encode(keyPair.getPublic());
        PublicKey decodedPublicKey = Pem.decoder().decodePublicKey(pem);

        assertThat(pem).contains("-----BEGIN PUBLIC KEY-----");
        assertThat(decodedPublicKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(decodedPublicKey.getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void pemEncoderAndDecoderRoundTripPrivateKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        byte[] encodedPem = Pem.encoder().encode(keyPair.getPrivate());
        PrivateKey decodedPrivateKey = Pem.decoder().decodePrivateKey(encodedPem);

        assertThat(new String(encodedPem, StandardCharsets.UTF_8)).contains("-----BEGIN PRIVATE KEY-----");
        assertThat(decodedPrivateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(decodedPrivateKey.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return keyPairGenerator.generateKeyPair();
    }
}
