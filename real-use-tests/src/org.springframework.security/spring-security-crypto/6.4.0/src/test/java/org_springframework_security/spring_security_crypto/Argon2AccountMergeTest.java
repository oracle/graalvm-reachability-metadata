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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2 password hashing through `Argon2PasswordEncoder` configured with an explicit salt
 * length, hash length, parallelism, memory cost, and iteration count.
 *
 * An account service merges the user table of a PHP and libsodium stack that stored PHC
 * `$argon2id$` and `$argon2i$` strings. It verifies those hashes without a password reset,
 * writes new hashes under the OWASP cost policy that other platforms can verify, rehashes
 * rows whose memory or iteration cost is below the policy on login, and refuses rows that
 * are not Argon2 hashes.
 */
public class Argon2AccountMergeTest {

    /// OWASP baseline for Argon2id: 19 MiB of memory, two passes, one lane.
    private static final int POLICY_MEMORY_KIB = 19 * 1024;
    private static final int POLICY_ITERATIONS = 2;

    /// Reference vectors from the Argon2 reference implementation, `password` salted with `somesalt`.
    private static final String REFERENCE_ARGON2ID =
            "$argon2id$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$CTFhFdXPJO1aFaMaO6Mm5c8y7cJHAph8ArZWb2GRPPc";
    private static final String REFERENCE_ARGON2I =
            "$argon2i$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$wWKIMhR9lyDFvRz9YTZweHKfbftvj+qf+YFY4NeBbtA";

    @Test
    void verifiesHashesWrittenByOtherArgon2Implementations() {
        AccountService accounts = new AccountService(policyEncoder());
        accounts.importRows(Map.of(
                "noor@shop.example", REFERENCE_ARGON2ID,
                "olga@shop.example", REFERENCE_ARGON2I));

        assertTrue(accounts.login("noor@shop.example", "password"));
        assertTrue(accounts.login("olga@shop.example", "password"));
        assertFalse(accounts.login("noor@shop.example", "Password"));
        assertFalse(accounts.login("olga@shop.example", "password "));
        assertFalse(accounts.login("pavel@shop.example", "password"), "unknown accounts never log in");
        assertEquals(REFERENCE_ARGON2ID, accounts.storedHash("noor@shop.example"),
                "a hash stronger than the policy is kept as it is");
        assertEquals(REFERENCE_ARGON2I, accounts.storedHash("olga@shop.example"));
    }

    @Test
    void writesPhcHashesUnderTheConfiguredPolicy() {
        AccountService accounts = new AccountService(policyEncoder());
        String password = "tr\u00e8s-s\u00fbr \u5bc6\u7801";

        accounts.register("quinn@shop.example", password);
        accounts.register("rosa@shop.example", password);

        String quinn = accounts.storedHash("quinn@shop.example");
        String rosa = accounts.storedHash("rosa@shop.example");
        assertTrue(quinn.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"), quinn);
        assertNotEquals(quinn, rosa, "every hash carries its own salt");

        String[] fields = quinn.split("\\$");
        assertEquals(6, fields.length, quinn);
        byte[] salt = Base64.getDecoder().decode(fields[4]);
        byte[] hash = Base64.getDecoder().decode(fields[5]);
        assertEquals(16, salt.length);
        assertEquals(32, hash.length);
        assertFalse(fields[5].endsWith("="), "PHC strings carry unpadded Base64");

        byte[] recomputed = OtherPlatform.argon2id(password, salt, POLICY_MEMORY_KIB, POLICY_ITERATIONS, 1, 32);
        assertArrayEquals(hash, recomputed, "another platform derives the same hash from the UTF-8 password");

        assertTrue(accounts.login("quinn@shop.example", password));
        assertFalse(accounts.login("quinn@shop.example", "tres-sur \u5bc6\u7801"));
        assertEquals(quinn, accounts.storedHash("quinn@shop.example"), "a hash at the policy is not rewritten");
    }

    @Test
    void rehashesRowsBelowThePolicyOnLogin() {
        Argon2PasswordEncoder policy = policyEncoder();
        AccountService accounts = new AccountService(policy);
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("sven@shop.example", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_2().encode("harbor-light"));
        rows.put("tara@shop.example", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("maple-crown"));
        rows.put("uma@shop.example",
                new Argon2PasswordEncoder(16, 32, 1, POLICY_MEMORY_KIB * 2, 1).encode("quiet-river"));
        rows.put("vera@shop.example", REFERENCE_ARGON2ID);
        accounts.importRows(rows);

        assertTrue(rows.get("sven@shop.example").startsWith("$argon2id$v=19$m=4096,t=3,p=1$"));
        assertTrue(rows.get("tara@shop.example").startsWith("$argon2id$v=19$m=16384,t=2,p=1$"));
        assertTrue(policy.upgradeEncoding(rows.get("sven@shop.example")), "memory below the policy");
        assertTrue(policy.upgradeEncoding(rows.get("tara@shop.example")), "memory below the policy");
        assertTrue(policy.upgradeEncoding(rows.get("uma@shop.example")), "iterations below the policy");
        assertFalse(policy.upgradeEncoding(rows.get("vera@shop.example")), "memory and iterations above it");

        assertFalse(accounts.login("sven@shop.example", "harbor-lights"));
        assertEquals(rows.get("sven@shop.example"), accounts.storedHash("sven@shop.example"),
                "a failed login never rewrites the row");

        assertTrue(accounts.login("sven@shop.example", "harbor-light"));
        assertTrue(accounts.login("tara@shop.example", "maple-crown"));
        assertTrue(accounts.login("uma@shop.example", "quiet-river"));
        assertTrue(accounts.login("vera@shop.example", "password"));
        assertEquals(List.of("sven@shop.example", "tara@shop.example", "uma@shop.example"), accounts.rehashed());

        for (String email : List.of("sven@shop.example", "tara@shop.example", "uma@shop.example")) {
            String upgraded = accounts.storedHash(email);
            assertTrue(upgraded.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"), upgraded);
            assertFalse(policy.upgradeEncoding(upgraded), email);
        }
        assertTrue(accounts.login("sven@shop.example", "harbor-light"), "the rehashed row still verifies");
        assertEquals(REFERENCE_ARGON2ID, accounts.storedHash("vera@shop.example"));
        assertEquals(3, accounts.rehashed().size(), "an upgraded row is rehashed only once");
    }

    @Test
    void verifiesRowsWithShorterHashesAndMoreLanes() {
        byte[] salt = "pepper-and-salt!".getBytes(StandardCharsets.US_ASCII);
        byte[] shortHash = OtherPlatform.argon2id("lantern-moss", salt, 8192, 3, 2, 16);
        String row = "$argon2id$v=19$m=8192,t=3,p=2$"
                + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$"
                + Base64.getEncoder().withoutPadding().encodeToString(shortHash);

        Argon2PasswordEncoder policy = policyEncoder();
        assertTrue(policy.matches("lantern-moss", row), "the row's own lanes and hash length are used");
        assertFalse(policy.matches("lantern-mosses", row));

        Argon2PasswordEncoder fourLanes = new Argon2PasswordEncoder(8, 16, 4, 8192, 3);
        String written = fourLanes.encode("lantern-moss");
        assertTrue(written.startsWith("$argon2id$v=19$m=8192,t=3,p=4$"), written);
        String[] fields = written.split("\\$");
        assertEquals(8, Base64.getDecoder().decode(fields[4]).length);
        assertEquals(16, Base64.getDecoder().decode(fields[5]).length);
        assertTrue(policy.matches("lantern-moss", written));
    }

    @Test
    void refusesRowsThatAreNotArgon2Hashes() {
        AccountService accounts = new AccountService(policyEncoder());
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("walt@shop.example", "$2y$10$abcdefghijklmnopqrstuuJ7nQ6v4Dk9d8KQe3y5XqgJx6t1x6m2e");
        rows.put("xena@shop.example", "$argon2id$v=19$m=lots,t=2,p=1$c29tZXNhbHQ$aGFzaA");
        rows.put("yuri@shop.example", "$argon2id$v=19");
        rows.put("zane@shop.example", REFERENCE_ARGON2ID.replace("$argon2id$", "$argon2x$"));
        accounts.importRows(rows);

        for (String email : rows.keySet()) {
            assertFalse(accounts.login(email, "password"), email);
        }
        assertEquals(List.of(), accounts.rehashed(), "unverifiable rows are never rehashed");
        assertEquals(rows.get("walt@shop.example"), accounts.storedHash("walt@shop.example"));

        Argon2PasswordEncoder policy = policyEncoder();
        assertFalse(policy.matches("password", null));
        assertFalse(policy.upgradeEncoding(null));
        assertFalse(policy.upgradeEncoding(""));
    }

    private static Argon2PasswordEncoder policyEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, POLICY_MEMORY_KIB, POLICY_ITERATIONS);
    }

    /// The merged user table: verifies logins and rehashes rows below the cost policy.
    private static final class AccountService {
        private final PasswordEncoder encoder;
        private final Map<String, String> rows = new LinkedHashMap<>();
        private final List<String> rehashed = new ArrayList<>();

        AccountService(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        void importRows(Map<String, String> imported) {
            rows.putAll(imported);
        }

        void register(String email, String rawPassword) {
            rows.put(email, encoder.encode(rawPassword));
        }

        String storedHash(String email) {
            return rows.get(email);
        }

        boolean login(String email, String rawPassword) {
            String stored = rows.get(email);
            if (stored == null || !encoder.matches(rawPassword, stored)) {
                return false;
            }
            if (encoder.upgradeEncoding(stored)) {
                rows.put(email, encoder.encode(rawPassword));
                rehashed.add(email);
            }
            return true;
        }

        List<String> rehashed() {
            return List.copyOf(rehashed);
        }
    }

    /// Derives Argon2id hashes the way another platform's Argon2 library does.
    private static final class OtherPlatform {

        static byte[] argon2id(String password, byte[] salt, int memoryKib, int iterations, int lanes, int length) {
            Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withSalt(salt)
                    .withMemoryAsKB(memoryKib)
                    .withIterations(iterations)
                    .withParallelism(lanes)
                    .build();
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            byte[] hash = new byte[length];
            generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);
            return hash;
        }
    }
}
