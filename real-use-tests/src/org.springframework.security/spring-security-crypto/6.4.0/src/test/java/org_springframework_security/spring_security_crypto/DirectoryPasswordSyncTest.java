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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.LdapShaPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * LDAP `userPassword` interchange through `LdapShaPasswordEncoder`, the encoder for the
 * RFC 2307 `{SHA}` and `{SSHA}` schemes that directory servers store.
 *
 * A directory synchronisation service pulls `userPassword` attributes out of an
 * OpenLDAP export, verifies logins against them without asking users to reset, writes
 * new salted hashes that the directory's own tools can verify, emits lower-case scheme
 * tags for a server that requires them, and parks entries whose scheme it cannot verify
 * so that an operator resets them.
 */
public class DirectoryPasswordSyncTest {

    @Test
    void verifiesHashesExportedFromTheDirectory() {
        Map<String, String> export = new LinkedHashMap<>();
        export.put("uid=alice,ou=people", Slappasswd.ssha("orchid-lantern", new byte[] {0x1f, 0x2e, 0x3d, 0x4c}));
        export.put("uid=bob,ou=people", Slappasswd.sha("granite-ferry"));
        export.put("uid=carol,ou=people",
                Slappasswd.ssha("violet-harbor", new byte[] {9, 8, 7, 6, 5, 4, 3, 2}).replace("{SSHA}", "{ssha}"));
        export.put("uid=dave,ou=people", Slappasswd.sha("cedar-signal").replace("{SHA}", "{sha}"));
        Map<String, String> passwords = Map.of(
                "uid=alice,ou=people", "orchid-lantern",
                "uid=bob,ou=people", "granite-ferry",
                "uid=carol,ou=people", "violet-harbor",
                "uid=dave,ou=people", "cedar-signal");

        DirectorySync sync = new DirectorySync(new LdapShaPasswordEncoder());
        sync.importEntries(export);

        assertEquals(List.of(), sync.parkedEntries());
        for (String dn : export.keySet()) {
            assertTrue(sync.login(dn, passwords.get(dn)), dn);
            assertFalse(sync.login(dn, passwords.get(dn) + "!"), dn);
            assertFalse(sync.login(dn, passwords.get(dn).toUpperCase()), dn);
        }
        assertFalse(sync.login("uid=mallory,ou=people", "orchid-lantern"));
    }

    @Test
    void writesSaltedHashesThatDirectoryToolsVerify() {
        DirectorySync sync = new DirectorySync(new LdapShaPasswordEncoder());

        sync.changePassword("uid=erin,ou=people", "copper-meadow");
        sync.changePassword("uid=frank,ou=people", "copper-meadow");

        String erin = sync.userPassword("uid=erin,ou=people");
        String frank = sync.userPassword("uid=frank,ou=people");
        assertTrue(erin.startsWith("{SSHA}"), erin);
        assertNotEquals(erin, frank, "every hash carries its own salt");

        byte[] decoded = Base64.getDecoder().decode(erin.substring("{SSHA}".length()));
        assertEquals(20 + 8, decoded.length, "a SHA-1 digest followed by an eight byte salt");
        byte[] salt = Arrays.copyOfRange(decoded, 20, decoded.length);
        assertEquals(erin, Slappasswd.ssha("copper-meadow", salt), "the directory recomputes the same value");
        assertArrayEquals(Arrays.copyOf(decoded, 20), Slappasswd.digest("copper-meadow", salt));

        assertTrue(sync.login("uid=erin,ou=people", "copper-meadow"));
        assertFalse(sync.login("uid=erin,ou=people", "copper-meadows"));
    }

    @Test
    void writesLowerCaseSchemesForServersThatRequireThem() {
        LdapShaPasswordEncoder encoder = new LdapShaPasswordEncoder();
        encoder.setForceLowerCasePrefix(true);
        DirectorySync sync = new DirectorySync(encoder);
        sync.importEntries(Map.of("uid=grace,ou=people", Slappasswd.ssha("amber-tunnel", new byte[] {1, 2, 3, 4})));

        sync.changePassword("uid=heidi,ou=people", "silver-quarry");
        String heidi = sync.userPassword("uid=heidi,ou=people");
        assertTrue(heidi.startsWith("{ssha}"), heidi);
        assertTrue(sync.login("uid=heidi,ou=people", "silver-quarry"));

        assertTrue(sync.login("uid=grace,ou=people", "amber-tunnel"), "upper-case exports still verify");
        assertFalse(sync.login("uid=grace,ou=people", "amber-tunnels"));
    }

    @Test
    void writesUnsaltedHashesForALegacyReplica() {
        DirectorySync sync = new DirectorySync(new LdapShaPasswordEncoder(KeyGenerators.shared(0)));

        sync.changePassword("uid=ivan,ou=people", "pewter-bridge");
        sync.changePassword("uid=judy,ou=people", "pewter-bridge");

        assertEquals(Slappasswd.sha("pewter-bridge"), sync.userPassword("uid=ivan,ou=people"));
        assertEquals(sync.userPassword("uid=ivan,ou=people"), sync.userPassword("uid=judy,ou=people"),
                "without a salt equal passwords hash alike");
        assertTrue(sync.login("uid=judy,ou=people", "pewter-bridge"));
        assertFalse(sync.login("uid=judy,ou=people", "pewter-bridges"));
    }

    @Test
    void parksEntriesWhoseSchemeCannotBeVerified() {
        Map<String, String> export = new LinkedHashMap<>();
        export.put("uid=ken,ou=people", "{CRYPT}$6$rounds=5000$saltsalt$abcdef");
        export.put("uid=leo,ou=people", "{SSHA");
        export.put("uid=mia,ou=people", Slappasswd.ssha("linen-compass", new byte[] {4, 3, 2, 1}));
        export.put("uid=ned,ou=people", "plain-cleartext");

        DirectorySync sync = new DirectorySync(new LdapShaPasswordEncoder());
        sync.importEntries(export);

        assertFalse(sync.login("uid=ken,ou=people", "anything"));
        assertFalse(sync.login("uid=leo,ou=people", "anything"));
        assertEquals(List.of("uid=ken,ou=people", "uid=leo,ou=people"), sync.parkedEntries());

        assertTrue(sync.login("uid=mia,ou=people", "linen-compass"));
        assertTrue(sync.login("uid=ned,ou=people", "plain-cleartext"), "an untagged value is cleartext");
        assertFalse(sync.login("uid=ned,ou=people", "plain-cleartexts"));
        assertEquals(2, sync.parkedEntries().size(), "verifiable entries are never parked");

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> new LdapShaPasswordEncoder().matches("anything", export.get("uid=ken,ou=people")));
        assertTrue(unknown.getMessage().contains("{CRYPT}"), unknown.getMessage());
    }

    @Test
    void carriesDirectoryHashesIntoADelegatingUserTable() {
        PasswordEncoder delegating = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String exported = Slappasswd.ssha("saffron-anchor", new byte[] {7, 7, 7, 7});

        String row = "{ldap}" + exported;
        assertTrue(delegating.matches("saffron-anchor", row));
        assertFalse(delegating.matches("saffron-anchors", row));
        assertTrue(delegating.upgradeEncoding(row), "the bcrypt default supersedes the directory hash");

        String upgraded = delegating.encode("saffron-anchor");
        assertTrue(upgraded.startsWith("{bcrypt}"), upgraded);
        assertTrue(delegating.matches("saffron-anchor", upgraded));
    }

    /// Mirrors the directory's `userPassword` attributes and verifies logins against them.
    private static final class DirectorySync {
        private final PasswordEncoder encoder;
        private final Map<String, String> entries = new LinkedHashMap<>();
        private final List<String> parked = new ArrayList<>();

        DirectorySync(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        void importEntries(Map<String, String> userPasswords) {
            entries.putAll(userPasswords);
        }

        void changePassword(String dn, String rawPassword) {
            entries.put(dn, encoder.encode(rawPassword));
        }

        String userPassword(String dn) {
            return entries.get(dn);
        }

        boolean login(String dn, String rawPassword) {
            String stored = entries.get(dn);
            if (stored == null) {
                return false;
            }
            try {
                return encoder.matches(rawPassword, stored);
            } catch (IllegalArgumentException unverifiable) {
                if (!parked.contains(dn)) {
                    parked.add(dn);
                }
                return false;
            }
        }

        List<String> parkedEntries() {
            return List.copyOf(parked);
        }
    }

    /// Computes `userPassword` values the way the directory's `slappasswd` tool does.
    private static final class Slappasswd {

        static String sha(String password) {
            return "{SHA}" + Base64.getEncoder().encodeToString(digest(password, new byte[0]));
        }

        static String ssha(String password, byte[] salt) {
            byte[] digest = digest(password, salt);
            byte[] combined = Arrays.copyOf(digest, digest.length + salt.length);
            System.arraycopy(salt, 0, combined, digest.length, salt.length);
            return "{SSHA}" + Base64.getEncoder().encodeToString(combined);
        }

        static byte[] digest(String password, byte[] salt) {
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                sha1.update(password.getBytes(StandardCharsets.UTF_8));
                sha1.update(salt);
                return sha1.digest();
            } catch (NoSuchAlgorithmException missing) {
                throw new IllegalStateException(missing);
            }
        }
    }
}
