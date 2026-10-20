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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

/**
 * Password storage through the `DelegatingPasswordEncoder` that
 * `PasswordEncoderFactories` builds, the encoder a Spring application wires as its
 * `PasswordEncoder` bean.
 *
 * An account service inherits a user table whose rows were hashed over the years with
 * plain text, salted MD5 and SHA-1 digests, PBKDF2, scrypt, and Argon2. It verifies each
 * login against whichever algorithm its `{id}` prefix names, re-hashes every outdated
 * credential to the current bcrypt default on a successful login, and keeps accepting
 * the unprefixed bcrypt hashes written before the table carried ids.
 */
public class LegacyPasswordMigrationTest {

    @Test
    void registersNewAccountsWithTheBcryptDefault() {
        AccountService accounts = new AccountService(PasswordEncoderFactories.createDelegatingPasswordEncoder());

        accounts.register("alice", "correct horse battery staple");
        accounts.register("bob", "correct horse battery staple");

        String alice = accounts.storedHash("alice");
        String bob = accounts.storedHash("bob");
        assertTrue(alice.startsWith("{bcrypt}$2a$10$"), alice);
        assertEquals("{bcrypt}".length() + 60, alice.length());
        assertNotEquals(alice, bob, "every hash carries its own salt");

        assertTrue(accounts.login("alice", "correct horse battery staple"));
        assertFalse(accounts.login("alice", "Correct horse battery staple"));
        assertFalse(accounts.login("mallory", "correct horse battery staple"));
        assertEquals(alice, accounts.storedHash("alice"), "a current hash is never rewritten");
        assertEquals(List.of(), accounts.upgradedUsers());
    }

    @Test
    void verifiesAndUpgradesEveryLegacyAlgorithmOnLogin() {
        Map<String, String> legacyRows = new LinkedHashMap<>();
        legacyRows.put("carol", "{noop}tulip-garden");
        legacyRows.put("dave", "{MD5}" + new MessageDigestPasswordEncoder("MD5").encode("harbor-lights"));
        legacyRows.put("erin", "{SHA-1}" + new MessageDigestPasswordEncoder("SHA-1").encode("mountain-pass"));
        legacyRows.put("frank", "{SHA-256}" + new MessageDigestPasswordEncoder("SHA-256").encode("river-stone"));
        legacyRows.put("grace", "{pbkdf2@SpringSecurity_v5_8}"
                + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("desert-wind"));
        legacyRows.put("heidi", "{scrypt@SpringSecurity_v5_8}"
                + SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8().encode("forest-trail"));
        legacyRows.put("ivan", "{argon2@SpringSecurity_v5_8}"
                + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("ocean-tide"));
        Map<String, String> passwords = Map.of(
                "carol", "tulip-garden",
                "dave", "harbor-lights",
                "erin", "mountain-pass",
                "frank", "river-stone",
                "grace", "desert-wind",
                "heidi", "forest-trail",
                "ivan", "ocean-tide");

        AccountService accounts = new AccountService(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        legacyRows.forEach(accounts::importRow);

        for (String user : legacyRows.keySet()) {
            assertFalse(accounts.login(user, "wrong-" + passwords.get(user)), user);
            assertEquals(legacyRows.get(user), accounts.storedHash(user), "a failed login keeps " + user);
        }
        for (String user : legacyRows.keySet()) {
            assertTrue(accounts.login(user, passwords.get(user)), user);
            String upgraded = accounts.storedHash(user);
            assertTrue(upgraded.startsWith("{bcrypt}$2a$10$"), user + " -> " + upgraded);
        }
        assertEquals(List.copyOf(legacyRows.keySet()), accounts.upgradedUsers());

        for (String user : legacyRows.keySet()) {
            assertTrue(accounts.login(user, passwords.get(user)), user + " after upgrade");
            assertFalse(accounts.login(user, "wrong-" + passwords.get(user)), user + " after upgrade");
        }
        assertEquals(legacyRows.size(), accounts.upgradedUsers().size(), "upgraded hashes stay as they are");
    }

    @Test
    void upgradesBcryptHashesBelowTheConfiguredStrength() {
        String weak = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("lantern-glow");
        String current = "{bcrypt}" + new BCryptPasswordEncoder(12).encode("lantern-glow");
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("noop", NoOpPasswordEncoder.getInstance());
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", encoders);

        assertTrue(encoder.upgradeEncoding(weak));
        assertFalse(encoder.upgradeEncoding(current));
        assertTrue(encoder.upgradeEncoding("{noop}lantern-glow"));

        AccountService accounts = new AccountService(encoder);
        accounts.importRow("judy", weak);
        accounts.importRow("ken", current);
        assertTrue(accounts.login("judy", "lantern-glow"));
        assertTrue(accounts.login("ken", "lantern-glow"));
        assertTrue(accounts.storedHash("judy").startsWith("{bcrypt}$2a$12$"), accounts.storedHash("judy"));
        assertEquals(current, accounts.storedHash("ken"));
        assertEquals(List.of("judy"), accounts.upgradedUsers());
    }

    @Test
    void acceptsUnprefixedBcryptRowsOnlyWhenConfiguredTo() {
        String unprefixed = new BCryptPasswordEncoder().encode("copper-kettle");

        DelegatingPasswordEncoder strict =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        IllegalArgumentException missingId =
                assertThrows(IllegalArgumentException.class, () -> strict.matches("copper-kettle", unprefixed));
        assertTrue(missingId.getMessage().contains("password encoding prefix"), missingId.getMessage());
        IllegalArgumentException unknownId = assertThrows(IllegalArgumentException.class,
                () -> strict.matches("copper-kettle", "{md2}0f1e2d3c"));
        assertTrue(unknownId.getMessage().contains("md2"), unknownId.getMessage());

        DelegatingPasswordEncoder lenient =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        lenient.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        AccountService accounts = new AccountService(lenient);
        accounts.importRow("leo", unprefixed);

        assertFalse(accounts.login("leo", "copper-kettles"));
        assertEquals(unprefixed, accounts.storedHash("leo"));
        assertTrue(accounts.login("leo", "copper-kettle"));
        assertTrue(accounts.storedHash("leo").startsWith("{bcrypt}$2a$10$"), accounts.storedHash("leo"));
        assertEquals(List.of("leo"), accounts.upgradedUsers());
    }

    @Test
    void storesHashesWithCustomIdDelimiters() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("argon2", encoders, "[", "]");

        String hash = encoder.encode("velvet-moon");
        assertTrue(hash.startsWith("[argon2]$argon2id$v=19$m=16384,t=2,p=1$"), hash);
        assertTrue(encoder.matches("velvet-moon", hash));
        assertFalse(encoder.matches("velvet-moons", hash));
        assertFalse(encoder.upgradeEncoding(hash));

        String pbkdf2 = "[pbkdf2]" + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("velvet-moon");
        assertTrue(encoder.matches("velvet-moon", pbkdf2));
        assertTrue(encoder.upgradeEncoding(pbkdf2));
    }

    /// Keeps a username-to-hash table and re-hashes outdated credentials on a successful login.
    private static final class AccountService {
        private final PasswordEncoder encoder;
        private final Map<String, String> table = new LinkedHashMap<>();
        private final List<String> upgraded = new ArrayList<>();

        AccountService(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        void register(String username, String rawPassword) {
            table.put(username, encoder.encode(rawPassword));
        }

        void importRow(String username, String storedHash) {
            table.put(username, storedHash);
        }

        boolean login(String username, String rawPassword) {
            Optional<String> stored = Optional.ofNullable(table.get(username));
            if (stored.isEmpty() || !encoder.matches(rawPassword, stored.get())) {
                return false;
            }
            if (encoder.upgradeEncoding(stored.get())) {
                table.put(username, encoder.encode(rawPassword));
                upgraded.add(username);
            }
            return true;
        }

        String storedHash(String username) {
            return table.get(username);
        }

        List<String> upgradedUsers() {
            return List.copyOf(upgraded);
        }
    }
}
