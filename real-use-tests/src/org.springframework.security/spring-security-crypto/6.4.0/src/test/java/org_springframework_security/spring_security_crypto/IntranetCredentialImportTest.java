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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.Md4PasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;

/**
 * Legacy digest password hashing through `StandardPasswordEncoder`, `Md4PasswordEncoder`, and a
 * `MessageDigestPasswordEncoder` configured with an iteration count and Base64 output.
 *
 * An intranet portal absorbs the user tables of three retired applications: a Spring
 * Security 3.1 wiki that hashed with a site-wide secret through `StandardPasswordEncoder`,
 * a helpdesk that stored MD4 digests, and a Spring Security 2 timesheet tool whose
 * `ShaPasswordEncoder` iterated SHA-256 and wrote Base64. The portal keeps one encoder per
 * source system, verifies each imported row with the encoder of the system it came from,
 * and refuses to build an encoder from a configuration the JDK cannot honour.
 */
public class IntranetCredentialImportTest {

    private static final String WIKI_SECRET = "wiki-site-secret-2011";

    private static final int TIMESHEET_ITERATIONS = 5000;

    @Test
    void verifiesWikiRowsHashedWithTheSiteSecret() {
        CredentialImporter portal = CredentialImporter.withLegacySystems();
        byte[] salt = {0x10, 0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe};
        portal.importRow("wiki", "alice", WikiExport.row(salt, WIKI_SECRET, "quill-and-ledger"));

        assertTrue(portal.login("alice", "quill-and-ledger"));
        assertFalse(portal.login("alice", "quill-and-ledgers"));

        PasswordEncoder otherSite = new StandardPasswordEncoder("another-site-secret");
        assertFalse(otherSite.matches("quill-and-ledger", portal.storedHash("alice")),
                "the site secret is part of every digest");
    }

    @Test
    void writesWikiHashesTheOldWikiCanStillVerify() {
        CredentialImporter portal = CredentialImporter.withLegacySystems();

        portal.resetPassword("wiki", "bob", "copper-kettle");
        portal.resetPassword("wiki", "carol", "copper-kettle");

        String bob = portal.storedHash("bob");
        assertEquals(80, bob.length(), "8 salt bytes and a 32-byte SHA-256 digest, hex encoded");
        assertTrue(bob.matches("[0-9a-f]+"), bob);
        assertNotEquals(bob, portal.storedHash("carol"), "every hash carries its own salt");

        byte[] stored = HexFormat.of().parseHex(bob);
        byte[] salt = Arrays.copyOfRange(stored, 0, 8);
        assertEquals(bob, WikiExport.row(salt, WIKI_SECRET, "copper-kettle"));
        assertTrue(portal.login("bob", "copper-kettle"));
        assertTrue(portal.login("carol", "copper-kettle"));
        assertFalse(portal.login("carol", "Copper-kettle"));
    }

    @Test
    void verifiesUnsaltedAndSaltedHelpdeskRows() {
        CredentialImporter portal = CredentialImporter.withLegacySystems();
        portal.importRow("helpdesk", "dave", "a448017aaf21d8525fc10ae87aa6729d");
        portal.importRow("helpdesk", "erin", "d9130a8164549fe818874806e1c7014b");
        portal.importRow("helpdesk", "frank", "31d6cfe0d16ae931b73c59d7e0c089c0");

        assertTrue(portal.login("dave", "abc"), "RFC 1320 test vector");
        assertTrue(portal.login("erin", "message digest"), "RFC 1320 test vector");
        assertTrue(portal.login("frank", ""), "an empty password still has a digest");
        assertFalse(portal.login("dave", "abd"));
        assertFalse(portal.login("frank", " "));

        portal.resetPassword("helpdesk", "grace", "teal-umbrella");
        portal.resetPassword("helpdesk", "heidi", "teal-umbrella");
        String grace = portal.storedHash("grace");
        int saltEnd = grace.indexOf('}');
        assertTrue(grace.startsWith("{"), grace);
        assertEquals(1 + 44 + 1, saltEnd + 1, "a braced Base64 salt of 32 random bytes");
        assertEquals(32, Base64.getDecoder().decode(grace.substring(1, saltEnd)).length);
        assertTrue(grace.substring(saltEnd + 1).matches("[0-9a-f]{32}"), grace);
        assertNotEquals(grace, portal.storedHash("heidi"));
        assertTrue(portal.login("grace", "teal-umbrella"));
        assertFalse(portal.login("grace", "teal-umbrellas"));
    }

    @Test
    void readsHelpdeskRowsExportedAsBase64() {
        Md4PasswordEncoder base64Md4 = new Md4PasswordEncoder();
        base64Md4.setEncodeHashAsBase64(true);
        String abc = Base64.getEncoder().encodeToString(HexFormat.of().parseHex("a448017aaf21d8525fc10ae87aa6729d"));

        assertTrue(base64Md4.matches("abc", abc));
        assertFalse(base64Md4.matches("abc", "a448017aaf21d8525fc10ae87aa6729d"), "the output encoding must agree");

        String written = base64Md4.encode("saffron-gate");
        String digest = written.substring(written.indexOf('}') + 1);
        assertEquals(16, Base64.getDecoder().decode(digest).length, "MD4 digests are 16 bytes");
        assertTrue(base64Md4.matches("saffron-gate", written));
        assertFalse(new Md4PasswordEncoder().matches("saffron-gate", written));
    }

    @Test
    void verifiesTimesheetRowsWithTheirIterationCount() {
        CredentialImporter portal = CredentialImporter.withLegacySystems();
        portal.importRow("timesheet", "ivan", TimesheetExport.row("{pepper-mill}", "night-shift", TIMESHEET_ITERATIONS));
        portal.importRow("timesheet", "judy", TimesheetExport.row("", "day-shift", TIMESHEET_ITERATIONS));

        assertTrue(portal.login("ivan", "night-shift"));
        assertFalse(portal.login("ivan", "night-shifts"));
        assertTrue(portal.login("judy", "day-shift"), "a row without a braced salt is unsalted");

        MessageDigestPasswordEncoder singlePass = new MessageDigestPasswordEncoder("SHA-256");
        singlePass.setEncodeHashAsBase64(true);
        assertFalse(singlePass.matches("night-shift", portal.storedHash("ivan")),
                "the iteration count is part of the hash");
        assertTrue(singlePass.matches("day-shift", TimesheetExport.row("", "day-shift", 1)));

        portal.resetPassword("timesheet", "ken", "swing-shift");
        String ken = portal.storedHash("ken");
        String salt = ken.substring(0, ken.indexOf('}') + 1);
        assertEquals(TimesheetExport.row(salt, "swing-shift", TIMESHEET_ITERATIONS), ken);
        assertEquals(46 + 44, ken.length(), "a braced salt followed by a Base64 SHA-256 digest");
    }

    @Test
    void refusesConfigurationsTheJdkCannotHonour() {
        IllegalStateException unknown = assertThrows(IllegalStateException.class,
                () -> new MessageDigestPasswordEncoder("SHA-4096"));
        assertEquals("No such hashing algorithm", unknown.getMessage());

        MessageDigestPasswordEncoder timesheet = new MessageDigestPasswordEncoder("SHA-256");
        assertThrows(IllegalArgumentException.class, () -> timesheet.setIterations(0));

        CredentialImporter portal = CredentialImporter.withLegacySystems();
        assertThrows(IllegalArgumentException.class, () -> portal.importRow("mainframe", "leo", "deadbeef"));
        assertFalse(portal.login("leo", "anything"), "a rejected row never becomes an account");
    }

    /** The portal's account store: one encoder per retired system and the rows imported from each. */
    static final class CredentialImporter {

        private final Map<String, PasswordEncoder> encodersBySystem;

        private final Map<String, String> systemByUser = new LinkedHashMap<>();

        private final Map<String, String> hashByUser = new LinkedHashMap<>();

        CredentialImporter(Map<String, PasswordEncoder> encodersBySystem) {
            this.encodersBySystem = encodersBySystem;
        }

        static CredentialImporter withLegacySystems() {
            MessageDigestPasswordEncoder timesheet = new MessageDigestPasswordEncoder("SHA-256");
            timesheet.setIterations(TIMESHEET_ITERATIONS);
            timesheet.setEncodeHashAsBase64(true);
            Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
            encoders.put("wiki", new StandardPasswordEncoder(WIKI_SECRET));
            encoders.put("helpdesk", new Md4PasswordEncoder());
            encoders.put("timesheet", timesheet);
            return new CredentialImporter(encoders);
        }

        void importRow(String system, String user, String hash) {
            encoderFor(system);
            this.systemByUser.put(user, system);
            this.hashByUser.put(user, hash);
        }

        void resetPassword(String system, String user, String rawPassword) {
            importRow(system, user, encoderFor(system).encode(rawPassword));
        }

        boolean login(String user, String rawPassword) {
            String system = this.systemByUser.get(user);
            if (system == null) {
                return false;
            }
            return encoderFor(system).matches(rawPassword, this.hashByUser.get(user));
        }

        String storedHash(String user) {
            return this.hashByUser.get(user);
        }

        private PasswordEncoder encoderFor(String system) {
            PasswordEncoder encoder = this.encodersBySystem.get(system);
            if (encoder == null) {
                throw new IllegalArgumentException("Unknown source system " + system);
            }
            return encoder;
        }
    }

    /** What the retired wiki wrote: hex of the salt followed by SHA-256 iterated 1024 times. */
    static final class WikiExport {

        private WikiExport() {
        }

        static String row(byte[] salt, String secret, String rawPassword) {
            byte[] input = concat(salt, secret.getBytes(StandardCharsets.UTF_8),
                    rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(concat(salt, iterate("SHA-256", input, 1024)));
        }
    }

    /** What the retired timesheet tool wrote: the braced salt, then Base64 of the iterated digest. */
    static final class TimesheetExport {

        private TimesheetExport() {
        }

        static String row(String bracedSalt, String rawPassword, int iterations) {
            byte[] digest = iterate("SHA-256", (rawPassword + bracedSalt).getBytes(StandardCharsets.UTF_8), iterations);
            return bracedSalt + Base64.getEncoder().encodeToString(digest);
        }
    }

    static byte[] iterate(String algorithm, byte[] input, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] value = input;
            for (int i = 0; i < iterations; i++) {
                value = digest.digest(value);
            }
            return value;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
