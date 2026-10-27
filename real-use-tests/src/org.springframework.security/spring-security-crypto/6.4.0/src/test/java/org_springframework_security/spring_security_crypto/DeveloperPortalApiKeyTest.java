/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.crypto.util.EncodingUtils;

/**
 * Random key and token generation through `KeyGenerators` and `Base64StringKeyGenerator`,
 * with the `Hex`, `Utf8`, and `EncodingUtils` codecs that frame the generated keys.
 *
 * A developer portal issues API keys made of a hex key id and a URL-safe Base64 secret,
 * stores only a SHA-256 digest of each secret, and authenticates presented keys against
 * it. It also hands out webhook signing secrets that subscribers use to HMAC their
 * payloads, keeps one deployment-wide key for the lifetime of the process, exports keys
 * in a compact binary envelope, and rejects malformed keys that clients paste back.
 */
public class DeveloperPortalApiKeyTest {

    @Test
    void issuesApiKeysAndAuthenticatesThemAgainstStoredDigests() throws GeneralSecurityException {
        ApiKeyRegistry registry = new ApiKeyRegistry();

        String aliceKey = registry.issue("alice");
        String bobKey = registry.issue("bob");

        assertTrue(aliceKey.matches("pk_[0-9a-f]{16}\\.[A-Za-z0-9_-]{43}"), aliceKey);
        assertNotEquals(aliceKey, bobKey);
        String aliceKeyId = aliceKey.substring(3, 19);
        String aliceSecret = aliceKey.substring(20);
        assertEquals(8, Hex.decode(aliceKeyId).length, "a key id is eight random bytes");
        assertEquals(32, Base64.getUrlDecoder().decode(aliceSecret).length, "a secret is 32 bytes");

        String storedDigest = registry.storedDigest(aliceKeyId);
        assertEquals(64, storedDigest.length());
        assertFalse(storedDigest.contains(aliceSecret), "the registry never stores the secret");
        assertEquals(sha256Hex(aliceSecret), storedDigest);

        assertEquals(Optional.of("alice"), registry.authenticate(aliceKey));
        assertEquals(Optional.of("bob"), registry.authenticate(bobKey));

        String forgedSecret = aliceKey.substring(0, 20) + bobKey.substring(20);
        assertEquals(Optional.empty(), registry.authenticate(forgedSecret));
        assertEquals(Optional.empty(), registry.authenticate("pk_zzzzzzzzzzzzzzzz." + aliceSecret));
        assertEquals(Optional.empty(), registry.authenticate("pk_abc." + aliceSecret));
        assertEquals(Optional.empty(), registry.authenticate("not-a-key"));

        registry.revoke(aliceKeyId);
        assertEquals(Optional.empty(), registry.authenticate(aliceKey));
        assertEquals(Optional.of("bob"), registry.authenticate(bobKey));
    }

    @Test
    void generatedKeyIdsAndSecretsDoNotRepeat() {
        StringKeyGenerator keyIds = KeyGenerators.string();
        StringKeyGenerator secrets = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 48);
        Set<String> seenIds = new HashSet<>();
        Set<String> seenSecrets = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            String keyId = keyIds.generateKey();
            String secret = secrets.generateKey();
            assertTrue(keyId.matches("[0-9a-f]{16}"), keyId);
            assertEquals(64, secret.length(), "48 bytes are 64 unpadded Base64 characters");
            seenIds.add(keyId);
            seenSecrets.add(secret);
        }

        assertEquals(200, seenIds.size());
        assertEquals(200, seenSecrets.size());
    }

    @Test
    void signsAndVerifiesWebhookPayloadsWithGeneratedSigningSecrets() throws GeneralSecurityException {
        BytesKeyGenerator signingSecrets = KeyGenerators.secureRandom(32);
        assertEquals(32, signingSecrets.getKeyLength());

        Map<String, String> subscribers = new LinkedHashMap<>();
        subscribers.put("https://hooks.acme.example/portal", new String(Hex.encode(signingSecrets.generateKey())));
        subscribers.put("https://ci.globex.example/notify", new String(Hex.encode(signingSecrets.generateKey())));

        String acmeSecret = subscribers.get("https://hooks.acme.example/portal");
        String globexSecret = subscribers.get("https://ci.globex.example/notify");
        assertTrue(acmeSecret.matches("[0-9a-f]{64}"), acmeSecret);
        assertNotEquals(acmeSecret, globexSecret);

        String payload = """
                {"event":"key.revoked","owner":"alice","note":"rotated \u00fcber-key"}""";
        String signature = sign(acmeSecret, payload);
        assertTrue(signature.matches("sha256=[0-9a-f]{64}"), signature);

        assertTrue(verify(acmeSecret, payload, signature));
        assertFalse(verify(globexSecret, payload, signature), "another subscriber's secret does not verify");
        assertFalse(verify(acmeSecret, payload.replace("alice", "mallory"), signature));
        assertFalse(verify(acmeSecret, payload, "sha256=" + "0".repeat(63)), "an odd-length signature");
        assertFalse(verify(acmeSecret, payload, "sha256=" + "g".repeat(64)), "a non-hex signature");

        BytesKeyGenerator defaultGenerator = KeyGenerators.secureRandom();
        assertEquals(8, defaultGenerator.getKeyLength());
        assertEquals(8, defaultGenerator.generateKey().length);
    }

    @Test
    void keepsOneDeploymentKeyForTheLifetimeOfTheProcess() {
        BytesKeyGenerator deploymentKey = KeyGenerators.shared(16);
        byte[] first = deploymentKey.generateKey();
        byte[] second = deploymentKey.generateKey();

        assertEquals(16, deploymentKey.getKeyLength());
        assertEquals(16, first.length);
        assertArrayEquals(first, second, "a shared generator hands out the same key every time");

        BytesKeyGenerator otherDeployment = KeyGenerators.shared(16);
        assertFalse(MessageDigest.isEqual(first, otherDeployment.generateKey()),
                "each shared generator draws its own key");
    }

    @Test
    void exportsAndImportsKeysInABinaryEnvelope() {
        byte[] keyId = KeyGenerators.secureRandom(8).generateKey();
        byte[] secret = KeyGenerators.secureRandom(32).generateKey();
        byte[] owner = Utf8.encode("j\u00f6rg@example.com");

        String exported = KeyEnvelope.export(keyId, secret, owner);
        assertTrue(exported.matches("[0-9a-f]+"), exported);
        assertEquals(2 * (1 + 8 + 32 + owner.length), exported.length());
        assertTrue(exported.startsWith("01"), "the envelope starts with its format version");

        KeyEnvelope imported = KeyEnvelope.parse(exported.toUpperCase());
        assertArrayEquals(keyId, imported.keyId());
        assertArrayEquals(secret, imported.secret());
        assertEquals("j\u00f6rg@example.com", imported.owner());
        assertEquals(exported, KeyEnvelope.export(imported.keyId(), imported.secret(), Utf8.encode(imported.owner())));

        assertThrows(IllegalArgumentException.class, () -> KeyEnvelope.parse("02" + exported.substring(2)));
        assertThrows(IllegalArgumentException.class, () -> KeyEnvelope.parse(exported.substring(0, 20)));
        assertThrows(IllegalArgumentException.class, () -> KeyEnvelope.parse(exported + "f"));

        byte[] brokenOwner = EncodingUtils.concatenate(new byte[] {1}, keyId, secret, new byte[] {(byte) 0xc3});
        IllegalArgumentException decoding = assertThrows(IllegalArgumentException.class,
                () -> KeyEnvelope.parse(new String(Hex.encode(brokenOwner))));
        assertEquals("Decoding failed", decoding.getMessage());
    }

    @Test
    void rejectsGeneratorSettingsTooWeakForSecrets() {
        String padded = new Base64StringKeyGenerator().generateKey();
        assertEquals(44, padded.length(), "32 bytes are 44 padded Base64 characters");
        assertTrue(padded.endsWith("="), padded);
        assertEquals(32, Base64.getDecoder().decode(padded).length);

        assertEquals(64, new Base64StringKeyGenerator(48).generateKey().length());

        assertThrows(IllegalArgumentException.class, () -> new Base64StringKeyGenerator(16));
        assertThrows(IllegalArgumentException.class,
                () -> new Base64StringKeyGenerator(Base64.getUrlEncoder(), 31));
        assertThrows(IllegalArgumentException.class, () -> new Base64StringKeyGenerator(null, 32));
    }

    private static String sha256Hex(String value) throws GeneralSecurityException {
        return new String(Hex.encode(MessageDigest.getInstance("SHA-256").digest(Utf8.encode(value))));
    }

    private static String sign(String hexSecret, String payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Hex.decode(hexSecret), "HmacSHA256"));
        return "sha256=" + new String(Hex.encode(mac.doFinal(Utf8.encode(payload))));
    }

    private static boolean verify(String hexSecret, String payload, String signatureHeader)
            throws GeneralSecurityException {
        byte[] presented;
        try {
            presented = Hex.decode(signatureHeader.substring("sha256=".length()));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        byte[] expected = Hex.decode(sign(hexSecret, payload).substring("sha256=".length()));
        return MessageDigest.isEqual(expected, presented);
    }

    /// Issues `pk_<key id>.<secret>` keys and keeps only the SHA-256 digest of each secret.
    private static final class ApiKeyRegistry {
        private final StringKeyGenerator keyIds = KeyGenerators.string();
        private final StringKeyGenerator secrets =
                new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);
        private final Map<String, String> owners = new LinkedHashMap<>();
        private final Map<String, String> digests = new LinkedHashMap<>();

        String issue(String owner) throws GeneralSecurityException {
            String keyId = keyIds.generateKey();
            String secret = secrets.generateKey();
            owners.put(keyId, owner);
            digests.put(keyId, sha256Hex(secret));
            return "pk_" + keyId + "." + secret;
        }

        Optional<String> authenticate(String presentedKey) throws GeneralSecurityException {
            int dot = presentedKey.indexOf('.');
            if (!presentedKey.startsWith("pk_") || dot < 0) {
                return Optional.empty();
            }
            String keyId = presentedKey.substring(3, dot);
            try {
                if (Hex.decode(keyId).length != 8) {
                    return Optional.empty();
                }
            } catch (IllegalArgumentException malformed) {
                return Optional.empty();
            }
            String stored = digests.get(keyId);
            if (stored == null) {
                return Optional.empty();
            }
            byte[] expected = Hex.decode(stored);
            byte[] presented = Hex.decode(sha256Hex(presentedKey.substring(dot + 1)));
            return MessageDigest.isEqual(expected, presented) ? Optional.of(owners.get(keyId)) : Optional.empty();
        }

        String storedDigest(String keyId) {
            return digests.get(keyId);
        }

        void revoke(String keyId) {
            owners.remove(keyId);
            digests.remove(keyId);
        }
    }

    /// A version byte, an 8-byte key id, a 32-byte secret, and the UTF-8 owner, hex-encoded.
    private record KeyEnvelope(byte[] keyId, byte[] secret, String owner) {
        private static final byte VERSION = 1;
        private static final int HEADER = 1 + 8 + 32;

        static String export(byte[] keyId, byte[] secret, byte[] owner) {
            return new String(Hex.encode(EncodingUtils.concatenate(new byte[] {VERSION}, keyId, secret, owner)));
        }

        static KeyEnvelope parse(String hex) {
            byte[] envelope = Hex.decode(hex);
            if (envelope.length <= HEADER) {
                throw new IllegalArgumentException("Envelope too short: " + envelope.length + " bytes");
            }
            if (envelope[0] != VERSION) {
                throw new IllegalArgumentException("Unsupported envelope version " + envelope[0]);
            }
            return new KeyEnvelope(
                    EncodingUtils.subArray(envelope, 1, 9),
                    EncodingUtils.subArray(envelope, 9, HEADER),
                    Utf8.decode(EncodingUtils.subArray(envelope, HEADER, envelope.length)));
        }
    }
}
