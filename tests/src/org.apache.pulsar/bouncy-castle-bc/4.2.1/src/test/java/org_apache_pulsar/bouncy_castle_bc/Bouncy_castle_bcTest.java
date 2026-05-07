/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.bouncy_castle_bc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import org.apache.pulsar.bcloader.BouncyCastleLoader;
import org.apache.pulsar.common.util.BCLoader;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class Bouncy_castle_bcTest {
    private static final String PROVIDER_NAME = "BC";

    @Test
    void loaderRegistersAndReturnsBouncyCastleProvider() {
        Provider provider = new BouncyCastleLoader().getProvider();

        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo(PROVIDER_NAME);
        assertThat(provider).isInstanceOf(BouncyCastleProvider.class);
        assertThat(Security.getProvider(PROVIDER_NAME)).isSameAs(provider);
        assertThat(BouncyCastleLoader.provider).isSameAs(provider);
        assertThat(provider.getInfo()).contains("BouncyCastle");
    }

    @Test
    void loaderSatisfiesPulsarBcLoaderContract() {
        BCLoader loader = new BouncyCastleLoader();

        assertThat(loader.getProvider()).isSameAs(Security.getProvider(PROVIDER_NAME));
        assertThatCode(() -> MessageDigest.getInstance("SHA-256", loader.getProvider()))
                .doesNotThrowAnyException();
    }

    @Test
    void providerAdvertisesCoreCryptographicServices() {
        Provider provider = new BouncyCastleLoader().getProvider();

        assertThat(provider.getService("MessageDigest", "SHA-256")).isNotNull();
        assertThat(provider.getService("Mac", "HMACSHA256")).isNotNull();
        assertThat(provider.getService("KeyPairGenerator", "RSA")).isNotNull();
        assertThat(provider.getService("Signature", "SHA256WITHRSA")).isNotNull();
        assertThat(provider.getService("Cipher", "AES")).isNotNull();
    }

    @Test
    void rsaSignatureGeneratedByBouncyCastleVerifiesWithBouncyCastle() throws Exception {
        Provider provider = new BouncyCastleLoader().getProvider();
        byte[] message = "pulsar-bouncy-castle-loader".getBytes(StandardCharsets.UTF_8);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", provider);
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();

        Signature signer = Signature.getInstance("SHA256withRSA", provider);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA", provider);
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message);

        assertThat(signature).isNotEmpty();
        assertThat(verifier.verify(signature)).isTrue();
        assertThat(signer.getProvider()).isSameAs(provider);
        assertThat(verifier.getProvider()).isSameAs(provider);
    }

    @Test
    void serviceDescriptorNamesThePublicLoaderClass() throws IOException {
        try (InputStream stream = BouncyCastleLoader.class.getClassLoader()
                .getResourceAsStream("META-INF/services/bouncy-castle.yaml")) {
            assertThat(stream).isNotNull();
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(descriptor).contains(
                    "name: bouncy-castle",
                    "description: loader for Bouncy Castle provider",
                    "bcLoaderClass: org.apache.pulsar.bcloader.BouncyCastleLoader");
        }
    }
}
