/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BouncyCastleAesCbcBytesEncryptor;
import org.springframework.security.crypto.encrypt.BouncyCastleAesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

/**
 * Symmetric encryption of data at rest through `Encryptors`, `AesBytesEncryptor`, and the
 * Bouncy Castle AES encryptors, keyed from a password and a `KeyGenerators` salt.
 *
 * A multi-tenant integration service keeps each tenant's third-party API credentials in
 * a settings table, encrypted with a master password and a per-tenant salt. It reads the
 * credentials back on demand, rejects rows that were tampered with, rotates the master
 * password, keeps a deterministic blind index so it can look a tenant up by contact email
 * without decrypting every row, and moves its stored blobs between the JCE and Bouncy
 * Castle implementations.
 */
public class TenantSecretVaultTest {

    private static final String MASTER_PASSWORD = "vault-master-2024";

    @Test
    void storesAndReadsBackTenantCredentialsWithPerTenantSalts() {
        StringKeyGenerator salts = KeyGenerators.string();
        SecretVault vault = new SecretVault(MASTER_PASSWORD, salts);

        vault.put("acme", "stripe", "sk_live_51Hacme");
        vault.put("acme", "github", "ghp_acmeToken");
        vault.put("globex", "stripe", "sk_live_51Hacme");

        String acmeSalt = vault.salt("acme");
        String globexSalt = vault.salt("globex");
        assertEquals(16, acmeSalt.length(), "an 8-byte salt in hex");
        assertTrue(acmeSalt.matches("[0-9a-f]{16}"), acmeSalt);
        assertNotEquals(acmeSalt, globexSalt);

        String acmeStripe = vault.storedCiphertext("acme", "stripe");
        String globexStripe = vault.storedCiphertext("globex", "stripe");
        assertTrue(acmeStripe.matches("[0-9a-f]+"), acmeStripe);
        assertTrue(!acmeStripe.contains(new String(Hex.encode(Utf8.encode("sk_live_51Hacme")))),
                "the stored value never contains the secret");
        assertNotEquals(acmeStripe, globexStripe, "equal secrets of different tenants never look alike");

        vault.put("acme", "stripe", "sk_live_51Hacme");
        assertNotEquals(acmeStripe, vault.storedCiphertext("acme", "stripe"), "every write uses a fresh IV");

        assertEquals(Optional.of("sk_live_51Hacme"), vault.get("acme", "stripe"));
        assertEquals(Optional.of("ghp_acmeToken"), vault.get("acme", "github"));
        assertEquals(Optional.of("sk_live_51Hacme"), vault.get("globex", "stripe"));
        assertEquals(Optional.empty(), vault.get("globex", "github"));
    }

    @Test
    void rejectsTamperedRowsAndTheWrongMasterPassword() {
        String salt = KeyGenerators.string().generateKey();
        TextEncryptor encryptor = Encryptors.delux(MASTER_PASSWORD, salt);
        String stored = encryptor.encrypt("xoxb-slack-bot-token");
        assertEquals("xoxb-slack-bot-token", encryptor.decrypt(stored));

        byte[] raw = Hex.decode(stored);
        raw[raw.length - 1] ^= 0x01;
        String tampered = new String(Hex.encode(raw));
        assertThrows(IllegalStateException.class, () -> encryptor.decrypt(tampered));

        TextEncryptor wrongPassword = Encryptors.delux("vault-master-2023", salt);
        assertThrows(IllegalStateException.class, () -> wrongPassword.decrypt(stored));
        TextEncryptor wrongSalt = Encryptors.delux(MASTER_PASSWORD, KeyGenerators.string().generateKey());
        assertThrows(IllegalStateException.class, () -> wrongSalt.decrypt(stored));
    }

    @Test
    void rotatesTheMasterPasswordWithoutLosingAnyCredential() {
        SecretVault vault = new SecretVault(MASTER_PASSWORD, KeyGenerators.string());
        vault.put("acme", "stripe", "sk_live_acme");
        vault.put("acme", "twilio", "AC0123456789abcdef");
        vault.put("initech", "sendgrid", "SG.initech.key");
        Map<String, String> before = vault.rows();

        SecretVault rotated = vault.rotate("vault-master-2025");

        Map<String, String> after = rotated.rows();
        assertEquals(before.keySet(), after.keySet());
        for (String row : before.keySet()) {
            assertNotEquals(before.get(row), after.get(row), row + " is re-encrypted");
        }
        assertEquals(Optional.of("sk_live_acme"), rotated.get("acme", "stripe"));
        assertEquals(Optional.of("AC0123456789abcdef"), rotated.get("acme", "twilio"));
        assertEquals(Optional.of("SG.initech.key"), rotated.get("initech", "sendgrid"));

        TextEncryptor oldKey = Encryptors.delux(MASTER_PASSWORD, rotated.salt("acme"));
        assertThrows(IllegalStateException.class, () -> oldKey.decrypt(after.get("acme/stripe")));
    }

    @Test
    void looksTenantsUpThroughADeterministicBlindIndex() {
        String indexSalt = KeyGenerators.string().generateKey();
        BytesKeyGenerator fixedIv = KeyGenerators.shared(16);
        assertArrayEquals(fixedIv.generateKey(), fixedIv.generateKey());
        BytesEncryptor index = new AesBytesEncryptor(MASTER_PASSWORD, indexSalt, fixedIv);

        Map<String, String> tenantsByIndex = new LinkedHashMap<>();
        tenantsByIndex.put(blindIndex(index, "ops@acme.example"), "acme");
        tenantsByIndex.put(blindIndex(index, "it@globex.example"), "globex");
        tenantsByIndex.put(blindIndex(index, "admin@initech.example"), "initech");

        assertEquals("globex", tenantsByIndex.get(blindIndex(index, "it@globex.example")));
        assertEquals("acme", tenantsByIndex.get(blindIndex(index, "ops@acme.example")));
        assertEquals(null, tenantsByIndex.get(blindIndex(index, "ops@umbrella.example")));
        assertEquals(3, tenantsByIndex.size());

        String acme = blindIndex(index, "ops@acme.example");
        byte[] decrypted = index.decrypt(Hex.decode(acme));
        assertEquals("ops@acme.example", Utf8.decode(decrypted));

        BytesEncryptor randomized = Encryptors.standard(MASTER_PASSWORD, indexSalt);
        assertNotEquals(new String(Hex.encode(randomized.encrypt(Utf8.encode("ops@acme.example")))),
                new String(Hex.encode(randomized.encrypt(Utf8.encode("ops@acme.example")))));
    }

    @Test
    void sharesStoredBlobsBetweenJceAndBouncyCastleEncryptors() {
        String salt = KeyGenerators.string().generateKey();
        byte[] payload = Utf8.encode("{\"clientId\":\"acme\",\"clientSecret\":\"s3cr3t\"}");

        BytesEncryptor jceGcm = Encryptors.stronger(MASTER_PASSWORD, salt);
        BytesEncryptor bcGcm = new BouncyCastleAesGcmBytesEncryptor(MASTER_PASSWORD, salt);
        assertArrayEquals(payload, bcGcm.decrypt(jceGcm.encrypt(payload)));
        assertArrayEquals(payload, jceGcm.decrypt(bcGcm.encrypt(payload)));
        assertEquals(16 + payload.length + 16, bcGcm.encrypt(payload).length, "IV, ciphertext and tag");

        BytesEncryptor jceCbc = Encryptors.standard(MASTER_PASSWORD, salt);
        BytesEncryptor bcCbc = new BouncyCastleAesCbcBytesEncryptor(MASTER_PASSWORD, salt);
        assertArrayEquals(payload, bcCbc.decrypt(jceCbc.encrypt(payload)));
        assertArrayEquals(payload, jceCbc.decrypt(bcCbc.encrypt(payload)));
        int padded = (payload.length / 16 + 1) * 16;
        assertEquals(16 + padded, jceCbc.encrypt(payload).length, "IV and PKCS#5-padded blocks");

        TextEncryptor hexText = Encryptors.text(MASTER_PASSWORD, salt);
        String stored = hexText.encrypt("rotate-me-quarterly");
        assertEquals("rotate-me-quarterly", Utf8.decode(bcCbc.decrypt(Hex.decode(stored))));
    }

    @Test
    void encryptsWithAKeyFromAnExternalKeyManagementService() {
        byte[] keyMaterial = KeyGenerators.secureRandom(32).generateKey();
        assertEquals(32, keyMaterial.length);
        SecretKeySpec dataKey = new SecretKeySpec(keyMaterial, "AES");
        BytesEncryptor encryptor = new AesBytesEncryptor(dataKey, KeyGenerators.secureRandom(16),
                AesBytesEncryptor.CipherAlgorithm.GCM);

        byte[] report = Utf8.encode("quarterly revenue: 1,204,331.07 EUR");
        byte[] sealed = encryptor.encrypt(report);
        assertArrayEquals(report, encryptor.decrypt(sealed));

        BytesEncryptor sameKey = new AesBytesEncryptor(new SecretKeySpec(keyMaterial.clone(), "AES"),
                KeyGenerators.secureRandom(16), AesBytesEncryptor.CipherAlgorithm.GCM);
        assertArrayEquals(report, sameKey.decrypt(sealed));

        TextEncryptor disabled = Encryptors.noOpText();
        assertEquals("plain-in-dev", disabled.encrypt("plain-in-dev"));
        assertEquals("plain-in-dev", disabled.decrypt("plain-in-dev"));
    }

    private static String blindIndex(BytesEncryptor index, String email) {
        return new String(Hex.encode(index.encrypt(Utf8.encode(email))));
    }

    /// Stores per-tenant credentials encrypted with a master password and a salt per tenant.
    private static final class SecretVault {
        private final String masterPassword;
        private final StringKeyGenerator saltGenerator;
        private final Map<String, String> salts = new LinkedHashMap<>();
        private final Map<String, String> rows = new LinkedHashMap<>();

        SecretVault(String masterPassword, StringKeyGenerator saltGenerator) {
            this.masterPassword = masterPassword;
            this.saltGenerator = saltGenerator;
        }

        void put(String tenant, String service, String secret) {
            rows.put(tenant + "/" + service, encryptor(tenant).encrypt(secret));
        }

        Optional<String> get(String tenant, String service) {
            return Optional.ofNullable(rows.get(tenant + "/" + service)).map(encryptor(tenant)::decrypt);
        }

        String salt(String tenant) {
            return salts.get(tenant);
        }

        String storedCiphertext(String tenant, String service) {
            return rows.get(tenant + "/" + service);
        }

        Map<String, String> rows() {
            return Map.copyOf(rows);
        }

        SecretVault rotate(String newMasterPassword) {
            SecretVault rotated = new SecretVault(newMasterPassword, saltGenerator);
            rotated.salts.putAll(salts);
            rows.forEach((row, ciphertext) -> {
                String tenant = row.substring(0, row.indexOf('/'));
                String secret = encryptor(tenant).decrypt(ciphertext);
                rotated.rows.put(row, rotated.encryptor(tenant).encrypt(secret));
            });
            return rotated;
        }

        private TextEncryptor encryptor(String tenant) {
            return Encryptors.delux(masterPassword, salts.computeIfAbsent(tenant, t -> saltGenerator.generateKey()));
        }
    }
}
