/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;

/**
 * Bcrypt hashing through `BCrypt` and `BCryptPasswordEncoder` with explicit `$2a`, `$2y`, and
 * `$2b` versions and cost factors, the scheme other platforms write into a shared user table.
 *
 * A Java service takes over the accounts of a PHP shop whose `password_hash()` wrote `$2y$`
 * rows. It reproduces the OpenBSD reference vectors to prove the hashes are interchangeable,
 * logs the imported users in without a reset, rehashes rows whose cost is below the service's
 * policy on their next login, and quarantines rows that are not bcrypt at all.
 */
public class PhpAccountImportTest {

    private static final int LEGACY_COST = 4;

    private static final int POLICY_COST = 6;

    @Test
    void reproducesTheReferenceVectorsOfOtherBcryptImplementations() {
        Map<String[], String> vectors = new LinkedHashMap<>();
        vectors.put(new String[] {"", "$2a$06$DCq7YPn5Rq63x1Lad4cll."},
                "$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s.");
        vectors.put(new String[] {"abc", "$2a$06$If6bvum7DFjUnE9p2uDeDu"},
                "$2a$06$If6bvum7DFjUnE9p2uDeDu0YHzrHM6tf.iqN8.yx.jNN1ILEf7h0i");
        vectors.put(new String[] {"U*U", "$2a$05$CCCCCCCCCCCCCCCCCCCCC."},
                "$2a$05$CCCCCCCCCCCCCCCCCCCCC.E5YPO9kmyuRGyh0XouQYb4YMJKvyOeW");

        for (Map.Entry<String[], String> vector : vectors.entrySet()) {
            String password = vector.getKey()[0];
            String salt = vector.getKey()[1];
            String expected = vector.getValue();
            assertEquals(expected, BCrypt.hashpw(password, salt));
            assertTrue(BCrypt.checkpw(password, expected));
            for (String minor : List.of("y", "b")) {
                String variant = "$2" + minor + expected.substring(3);
                assertEquals(variant, BCrypt.hashpw(password, "$2" + minor + salt.substring(3)),
                        "the $2" + minor + " digest of an ASCII password equals the $2a digest");
                assertTrue(new BCryptPasswordEncoder().matches(password, variant), variant);
            }
        }
    }

    @Test
    void logsImportedPhpUsersInAndUpgradesTheirCostOnLogin() {
        Map<String, String> passwords = Map.of(
                "amelia@shop.example", "paper-lantern-7",
                "bruno@shop.example", "quiet-harbor-22",
                "chiara@shop.example", "Gr\u00fcnerHafen!");
        Map<String, String> phpRows = new LinkedHashMap<>();
        for (Map.Entry<String, String> user : passwords.entrySet()) {
            phpRows.put(user.getKey(), PhpPasswordHash.hash(user.getValue(), LEGACY_COST));
        }
        phpRows.put("dmitri@shop.example", PhpPasswordHash.hash("orbit-signal", POLICY_COST));

        AccountStore store = new AccountStore(new BCryptPasswordEncoder(BCryptVersion.$2B, POLICY_COST));
        store.importRows(phpRows);

        assertEquals(List.of(), store.quarantined());
        for (Map.Entry<String, String> user : passwords.entrySet()) {
            String imported = store.row(user.getKey());
            assertTrue(imported.startsWith("$2y$04$"), imported);

            assertFalse(store.login(user.getKey(), user.getValue() + " "));
            assertEquals(imported, store.row(user.getKey()), "a failed login leaves the row alone");

            assertTrue(store.login(user.getKey(), user.getValue()));
            String upgraded = store.row(user.getKey());
            assertTrue(upgraded.startsWith("$2b$06$"), upgraded);
            assertEquals(60, upgraded.length());
            assertTrue(PhpPasswordHash.verify(user.getValue(), upgraded),
                    "the PHP shop still verifies the upgraded row during the cut-over");

            assertTrue(store.login(user.getKey(), user.getValue()));
            assertEquals(upgraded, store.row(user.getKey()), "a row at policy cost is not rehashed again");
        }

        String atPolicy = store.row("dmitri@shop.example");
        assertTrue(store.login("dmitri@shop.example", "orbit-signal"));
        assertEquals(atPolicy, store.row("dmitri@shop.example"), "an imported row at policy cost is kept");
        assertEquals(List.of("amelia@shop.example", "bruno@shop.example", "chiara@shop.example"),
                store.upgradedAccounts().stream().sorted().toList());
        assertFalse(store.login("nobody@shop.example", "paper-lantern-7"));
    }

    @Test
    void writesTheVersionAndCostEachConsumerOfTheTableExpects() {
        String password = "teal-compass";
        Map<BCryptVersion, String> prefixes = Map.of(
                BCryptVersion.$2A, "$2a$05$",
                BCryptVersion.$2Y, "$2y$05$",
                BCryptVersion.$2B, "$2b$05$");

        for (Map.Entry<BCryptVersion, String> version : prefixes.entrySet()) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(version.getKey(), 5);
            String first = encoder.encode(password);
            String second = encoder.encode(password);

            assertTrue(first.startsWith(version.getValue()), first);
            assertNotEquals(first, second, "every hash carries a fresh 16-byte salt");
            assertTrue(encoder.matches(password, first));
            assertTrue(encoder.matches(password, second));
            assertFalse(encoder.upgradeEncoding(first));
            assertTrue(new BCryptPasswordEncoder(version.getKey(), 7).upgradeEncoding(first));
            assertEquals(first, BCrypt.hashpw(password, first), "the stored hash is its own salt");
        }

        String salt = BCrypt.gensalt("$2y", 8);
        assertTrue(salt.startsWith("$2y$08$"), salt);
        assertEquals(29, salt.length());
        String hash = BCrypt.hashpw("teal-compass", salt);
        assertEquals(salt, hash.substring(0, 29));
    }

    @Test
    void hashesNonAsciiPasswordsAsUtf8Bytes() {
        String password = "\u00e9t\u00e9-\u65e5\u672c-\u0436\u0443\u043a";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptVersion.$2Y, 4);

        String hash = encoder.encode(password);

        assertTrue(BCrypt.checkpw(password.getBytes(StandardCharsets.UTF_8), hash));
        assertEquals(hash, BCrypt.hashpw(password.getBytes(StandardCharsets.UTF_8), hash));
        assertFalse(BCrypt.checkpw(password.getBytes(StandardCharsets.ISO_8859_1), hash));
        assertFalse(encoder.matches("ete-\u65e5\u672c-\u0436\u0443\u043a", hash));
    }

    @Test
    void quarantinesRowsThatAreNotBcryptAndRejectsInvalidSettings() {
        Map<String, String> phpRows = new LinkedHashMap<>();
        phpRows.put("erik@shop.example", PhpPasswordHash.hash("granite-bay", LEGACY_COST));
        phpRows.put("fatima@shop.example", "5f4dcc3b5aa765d61d8327deb882cf99");
        phpRows.put("gustav@shop.example", "$argon2id$v=19$m=65536,t=4,p=1$c2FsdHNhbHQ$aGFzaGhhc2g");
        phpRows.put("hana@shop.example", "");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptVersion.$2B, POLICY_COST);
        AccountStore store = new AccountStore(encoder);
        store.importRows(phpRows);

        assertEquals(List.of("fatima@shop.example", "gustav@shop.example", "hana@shop.example"),
                store.quarantined());
        assertTrue(store.login("erik@shop.example", "granite-bay"));
        assertFalse(store.login("fatima@shop.example", "password"));
        assertFalse(encoder.matches("password", "5f4dcc3b5aa765d61d8327deb882cf99"));
        assertFalse(encoder.matches("password", ""));
        assertThrows(IllegalArgumentException.class,
                () -> encoder.upgradeEncoding("5f4dcc3b5aa765d61d8327deb882cf99"));
        assertThrows(IllegalArgumentException.class, () -> encoder.matches(null, store.row("erik@shop.example")));
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordEncoder(3));
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordEncoder(32));
        assertThrows(IllegalArgumentException.class, () -> BCrypt.gensalt("$2c", 10));
        assertThrows(IllegalArgumentException.class, () -> BCrypt.hashpw("granite-bay", "$2x$04$short"));
    }

    /**
     * The hashes the PHP shop wrote and still verifies: `password_hash()` with `PASSWORD_BCRYPT`
     * emits `$2y$` and `password_verify()` accepts any bcrypt minor version.
     */
    static final class PhpPasswordHash {

        private PhpPasswordHash() {
        }

        static String hash(String password, int cost) {
            return BCrypt.hashpw(password, BCrypt.gensalt("$2y", cost));
        }

        static boolean verify(String password, String hash) {
            return BCrypt.checkpw(password, hash);
        }
    }

    /**
     * The Java service's account table: verifies with the policy encoder and rehashes a row
     * whose cost is below policy once its owner has proved the password.
     */
    static final class AccountStore {

        private final BCryptPasswordEncoder encoder;

        private final Map<String, String> rows = new LinkedHashMap<>();

        private final List<String> quarantined = new ArrayList<>();

        private final List<String> upgraded = new ArrayList<>();

        AccountStore(BCryptPasswordEncoder encoder) {
            this.encoder = encoder;
        }

        void importRows(Map<String, String> phpRows) {
            for (Map.Entry<String, String> row : phpRows.entrySet()) {
                if (row.getValue().matches("\\$2[aby]\\$\\d\\d\\$[./0-9A-Za-z]{53}")) {
                    rows.put(row.getKey(), row.getValue());
                } else {
                    quarantined.add(row.getKey());
                }
            }
        }

        boolean login(String email, String password) {
            String stored = rows.get(email);
            if (stored == null || !encoder.matches(password, stored)) {
                return false;
            }
            if (encoder.upgradeEncoding(stored)) {
                rows.put(email, encoder.encode(password));
                upgraded.add(email);
            }
            return true;
        }

        String row(String email) {
            return rows.get(email);
        }

        List<String> quarantined() {
            return quarantined;
        }

        List<String> upgradedAccounts() {
            return upgraded;
        }
    }
}
