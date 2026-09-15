/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

import javax.crypto.KeyAgreement;

import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.config.hosts.KnownHostDigest;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.config.VersionProperties;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.digest.Digest;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.EventListenerUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.junit.jupiter.api.Test;

public class Sshd_commonTest {
    private static final byte[] AES_KEY = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV = "FEDCBA9876543210".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptsAuthenticatesDigestsAndCompressesSshPayloads() throws Exception {
        byte[] clearText = "0123456789ABCDEFfedcba9876543210".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = clearText.clone();
        Cipher encryptor = BuiltinCiphers.aes128ctr.create();
        encryptor.init(Cipher.Mode.Encrypt, AES_KEY, AES_IV);
        encryptor.update(encrypted);

        assertThat(encrypted).isNotEqualTo(clearText);

        Cipher decryptor = BuiltinCiphers.aes128ctr.create();
        decryptor.init(Cipher.Mode.Decrypt, AES_KEY, AES_IV);
        decryptor.update(encrypted);
        assertThat(encrypted).isEqualTo(clearText);

        Digest digest = BuiltinDigests.sha256.create();
        digest.init();
        digest.update(clearText);
        assertThat(digest.digest()).isEqualTo(SecurityUtils.getMessageDigest("SHA-256").digest(clearText));

        Mac firstMac = BuiltinMacs.hmacsha256.create();
        firstMac.init(AES_KEY);
        firstMac.update(clearText);
        byte[] firstAuthenticationCode = firstMac.doFinal();

        Mac secondMac = BuiltinMacs.hmacsha256.create();
        secondMac.init(AES_KEY);
        secondMac.update(clearText);
        assertThat(secondMac.doFinal()).isEqualTo(firstAuthenticationCode);

        byte[] repeatedPayload = "compressible SSH payload ".repeat(32).getBytes(StandardCharsets.UTF_8);
        ByteArrayBuffer compressed = new ByteArrayBuffer();
        compressed.putRawBytes(repeatedPayload, 0, repeatedPayload.length);
        Compression compression = BuiltinCompressions.zlib.create();
        compression.init(Compression.Type.Deflater, Deflater.DEFAULT_COMPRESSION);
        compression.compress(compressed);
        assertThat(compressed.available()).isLessThan(repeatedPayload.length);

        ByteArrayBuffer restored = new ByteArrayBuffer();
        compression.uncompress(compressed, restored);
        assertThat(Arrays.copyOf(restored.array(), restored.wpos())).isEqualTo(repeatedPayload);
    }

    @Test
    void generatesEncodesAndSignsRsaKeys() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String encodedPublicKey = PublicKeyEntry.toString(keyPair.getPublic());
        PublicKeyEntry parsedPublicKey = PublicKeyEntry.parsePublicKeyEntry(encodedPublicKey);
        PublicKey resolvedPublicKey = parsedPublicKey.resolvePublicKey(null, null, null);

        assertThat(KeyUtils.compareKeys(keyPair.getPublic(), resolvedPublicKey)).isTrue();
        assertThat(KeyUtils.getFingerPrint(resolvedPublicKey)).contains(":");

        ByteArrayBuffer keyPairBuffer = new ByteArrayBuffer();
        keyPairBuffer.putKeyPair(keyPair);
        keyPairBuffer.rpos(0);
        assertThat(KeyUtils.compareKeyPairs(keyPair, keyPairBuffer.getKeyPair())).isTrue();

        byte[] payload = "data signed through the SSH signature API".getBytes(StandardCharsets.UTF_8);
        Signature signer = BuiltinSignatures.rsaSHA256.create();
        signer.initSigner(null, keyPair.getPrivate());
        signer.update(null, payload);
        byte[] signature = signer.sign(null);

        Signature verifier = BuiltinSignatures.rsaSHA256.create();
        verifier.initVerifier(null, keyPair.getPublic());
        verifier.update(null, payload);
        assertThat(verifier.verify(null, signature)).isTrue();

        byte[] changedPayload = payload.clone();
        changedPayload[0] ^= 1;
        Signature changedPayloadVerifier = BuiltinSignatures.rsaSHA256.create();
        changedPayloadVerifier.initVerifier(null, keyPair.getPublic());
        changedPayloadVerifier.update(null, changedPayload);
        assertThat(changedPayloadVerifier.verify(null, signature)).isFalse();
    }

    @Test
    void createsJcaEntitiesThroughSecurityUtils() throws Exception {
        KeyPairGenerator ecGenerator = SecurityUtils.getKeyPairGenerator(KeyUtils.EC_ALGORITHM);
        ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair firstPair = ecGenerator.generateKeyPair();
        KeyPair secondPair = ecGenerator.generateKeyPair();

        KeyAgreement firstAgreement = SecurityUtils.getKeyAgreement("ECDH");
        firstAgreement.init(firstPair.getPrivate());
        firstAgreement.doPhase(secondPair.getPublic(), true);
        KeyAgreement secondAgreement = SecurityUtils.getKeyAgreement("ECDH");
        secondAgreement.init(secondPair.getPrivate());
        secondAgreement.doPhase(firstPair.getPublic(), true);
        assertThat(firstAgreement.generateSecret()).isEqualTo(secondAgreement.generateSecret());

        AlgorithmParameters parameters = SecurityUtils.getAlgorithmParameters(KeyUtils.EC_ALGORITHM);
        parameters.init(new ECGenParameterSpec("secp256r1"));
        assertThat(parameters.getParameterSpec(ECParameterSpec.class).getCurve()).isNotNull();

        KeyPair rsaKeyPair = generateRsaKeyPair();
        KeyFactory keyFactory = SecurityUtils.getKeyFactory(KeyUtils.RSA_ALGORITHM);
        PublicKey rebuiltPublicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(rsaKeyPair.getPublic().getEncoded()));
        assertThat(KeyUtils.compareKeys(rsaKeyPair.getPublic(), rebuiltPublicKey)).isTrue();
        assertThat(SecurityUtils.getCertificateFactory("X.509").getType()).isEqualTo("X.509");
    }

    @Test
    void parsesAndResolvesOpenSshHostConfiguration() throws Exception {
        String configuration = """
                Host build-*
                    HostName %h.internal.example
                    User deploy
                    Port 2222
                    ProxyJump gateway.example
                    IdentityFile ~/.ssh/build_key
                    IdentitiesOnly yes
                Host *
                    User fallback
                    Port 22
                """;

        List<HostConfigEntry> entries = HostConfigEntry.readHostConfigEntries(new StringReader(configuration), true);
        HostConfigEntryResolver resolver = HostConfigEntry.toHostConfigEntryResolver(entries);
        HostConfigEntry effective = resolver.resolveEffectiveHost("build-17", 0, null, null, null, null);

        assertThat(effective.getHostName()).isEqualTo("build-17.internal.example");
        assertThat(effective.getUsername()).isEqualTo("deploy");
        assertThat(effective.getPort()).isEqualTo(2222);
        assertThat(effective.getProxyJump()).isEqualTo("gateway.example");
        assertThat(effective.isIdentitiesOnly()).isTrue();
        assertThat(effective.getIdentities()).singleElement().satisfies(identity -> {
            assertThat(Path.of(identity)).isAbsolute();
            assertThat(identity).endsWith(".ssh" + File.separator + "build_key");
        });

        StringBuilder rendered = HostConfigEntry.appendHostConfigEntries(new StringBuilder(), entries);
        List<HostConfigEntry> reparsed = HostConfigEntry.readHostConfigEntries(
                new StringReader(rendered.toString()), true);
        assertThat(reparsed).hasSameSizeAs(entries);
    }

    @Test
    void parsesHashedKnownHostsAndAuthorizedKeys() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String encodedPublicKey = PublicKeyEntry.toString(keyPair.getPublic());
        AuthorizedKeyEntry authorizedKey = AuthorizedKeyEntry.parseAuthorizedKeyEntry(
                "command=\"echo accepted\",no-port-forwarding " + encodedPublicKey + " integration-key");

        assertThat(authorizedKey.getLoginOptions())
                .containsEntry("command", "echo accepted")
                .containsEntry("no-port-forwarding", "true");
        assertThat(authorizedKey.getComment()).isEqualTo("integration-key");
        assertThat(KeyUtils.compareKeys(keyPair.getPublic(), authorizedKey.resolvePublicKey(null, null))).isTrue();

        byte[] salt = "known-host-test-salt".getBytes(StandardCharsets.UTF_8);
        byte[] hash = KnownHostHashValue.calculateHashValue(
                "server.example", 2200, KnownHostDigest.SHA1, salt);
        KnownHostHashValue hashValue = new KnownHostHashValue();
        hashValue.setDigester(KnownHostDigest.SHA1);
        hashValue.setSaltValue(salt);
        hashValue.setDigestValue(hash);

        KnownHostEntry knownHost = KnownHostEntry.parseKnownHostEntry(hashValue + " " + encodedPublicKey);
        assertThat(knownHost.isHostMatch("server.example", 2200)).isTrue();
        assertThat(knownHost.isHostMatch("server.example", 22)).isFalse();
        assertThat(KeyUtils.compareKeys(
                keyPair.getPublic(), knownHost.getKeyEntry().resolvePublicKey(null, null))).isTrue();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void dispatchesFutureListenersThroughLibraryProxy() {
        AtomicInteger notifications = new AtomicInteger();
        SshFutureListener<CloseFuture> first = future -> notifications.incrementAndGet();
        SshFutureListener<CloseFuture> second = future -> notifications.addAndGet(10);
        Set<SshFutureListener<CloseFuture>> listeners
                = EventListenerUtils.synchronizedListenersSet(List.of(first, first, second));
        SshFutureListener<CloseFuture> dispatcher
                = EventListenerUtils.proxyWrapper((Class) SshFutureListener.class, listeners);

        dispatcher.operationComplete(null);

        assertThat(listeners).hasSize(2);
        assertThat(notifications).hasValue(11);
    }

    @Test
    void loadsPackagedVersionProperties() {
        NavigableMap<String, String> properties = VersionProperties.getVersionProperties();

        assertThat(properties).containsKey(VersionProperties.REPORTED_VERSION);
        assertThat(properties.get(VersionProperties.REPORTED_VERSION)).isNotBlank();
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = SecurityUtils.getKeyPairGenerator(KeyUtils.RSA_ALGORITHM);
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
