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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;
import org.springframework.security.crypto.util.EncodingUtils;

/**
 * PBKDF2 password hashing through `Pbkdf2PasswordEncoder` configured with an explicit
 * `SecretKeyFactoryAlgorithm`, salt length, iteration count, secret pepper and output encoding.
 *
 * A Java service takes over the user table of a Django site. It verifies the
 * `pbkdf2_sha256` and `pbkdf2_sha1` hashes Django wrote by mapping each row's salt and
 * iteration count onto an encoder, re-hashes a user on login with a peppered
 * PBKDF2-HMAC-SHA512 encoder whose pepper never reaches the table, and refuses malformed
 * rows instead of letting them log in.
 */
public class DjangoUserTableMigrationTest {

    /// Hashes computed with Python's `hashlib.pbkdf2_hmac`, exactly as Django stores them.
    private static final String DJANGO_LETMEIN =
            "pbkdf2_sha256$36000$seasalt2$QkIBVCvGmTmyjPJ5yox2y/jQB8isvgUNK98FxOU1UYo=";
    private static final String DJANGO_HARBOR =
            "pbkdf2_sha256$120000$Xb7kQ2mN9pRt4vWz$4W0+D7ua6/zVQqZRmm2Xz7hhckFnziXCjRU1Hn04ML8=";
    private static final String DJANGO_RIVER =
            "pbkdf2_sha1$20000$oldsalt42$agx3rJbvIbAPvZo75chBn5X+6DA=";

    private static final String PEPPER = "kms:users/pepper-2024";

    @Test
    void verifiesHashesWrittenByDjango() {
        UserTable table = new UserTable(PEPPER);
        table.importRow("amelie", DJANGO_LETMEIN);
        table.importRow("bruno", DJANGO_HARBOR);
        table.importRow("chloe", DJANGO_RIVER);

        assertFalse(table.login("amelie", "letmein"), "the accent is part of the password");
        assertFalse(table.login("chloe", "Quiet-river"));
        assertEquals(DJANGO_LETMEIN, table.row("amelie"), "a failed login leaves the row alone");

        assertTrue(table.login("amelie", "l\u00e8tmein"));
        assertTrue(table.login("bruno", "harbor-lights"));
        assertTrue(table.login("chloe", "quiet-river"));
        assertFalse(table.login("erika", "harbor-lights"), "unknown users never log in");
        assertEquals(List.of(), table.rejectedUsers());
    }

    @Test
    void upgradesToPepperedSha512OnLogin() {
        UserTable table = new UserTable(PEPPER);
        table.importRow("chloe", DJANGO_RIVER);
        table.importRow("felix", DJANGO_RIVER);

        assertTrue(table.login("chloe", "quiet-river"));
        assertTrue(table.login("felix", "quiet-river"));

        String chloe = table.row("chloe");
        String felix = table.row("felix");
        assertTrue(chloe.startsWith(UserTable.NATIVE_PREFIX), chloe);
        assertEquals(UserTable.NATIVE_PREFIX.length() + 2 * (16 + 64), chloe.length(),
                "hex of a 16-byte salt followed by a 512-bit derived key");
        assertTrue(chloe.substring(UserTable.NATIVE_PREFIX.length()).matches("[0-9a-f]+"), chloe);
        assertFalse(chloe.contains(PEPPER), "the pepper stays out of the table");
        assertNotEquals(chloe, felix, "every upgraded row carries its own salt");

        assertTrue(table.login("chloe", "quiet-river"));
        assertFalse(table.login("chloe", "quiet-river "));
        assertEquals(chloe, table.row("chloe"), "an upgraded row is not re-hashed again");

        UserTable restoredWithPepper = new UserTable(PEPPER);
        restoredWithPepper.importRow("chloe", chloe);
        assertTrue(restoredWithPepper.login("chloe", "quiet-river"));

        UserTable stolenDump = new UserTable("guessed-pepper");
        stolenDump.importRow("chloe", chloe);
        assertFalse(stolenDump.login("chloe", "quiet-river"), "the hash is useless without the pepper");
    }

    @Test
    void storedHashOnlyVerifiesUnderItsOwnParameters() {
        DjangoHash django = DjangoHash.parse(DJANGO_LETMEIN);
        String springEncoded = django.springEncoded();
        String password = "l\u00e8tmein";

        assertTrue(django.encoder().matches(password, springEncoded));
        assertFalse(base64Encoder("", 8, 35_999, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256)
                .matches(password, springEncoded), "the iteration count is part of the hash");
        assertFalse(base64Encoder("", 8, 36_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA512)
                .matches(password, springEncoded), "the PRF is part of the hash");
        assertFalse(base64Encoder("pepper", 8, 36_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256)
                .matches(password, springEncoded), "a secret is appended to the salt");
        assertFalse(base64Encoder("", 7, 36_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256)
                .matches(password, springEncoded), "the salt length tells where the salt ends");

        Pbkdf2PasswordEncoder hexEncoder =
                new Pbkdf2PasswordEncoder("", 8, 36_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        String hexEncoded = new String(Hex.encode(Base64.getDecoder().decode(springEncoded)));
        assertTrue(hexEncoder.matches(password, hexEncoded), "the same bytes verify when hex-encoded");
        assertTrue(hexEncoded.startsWith(new String(Hex.encode(Utf8.encode("seasalt2")))), hexEncoded);

        Pbkdf2PasswordEncoder sha1 = base64Encoder("", 9, 20_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA1);
        String fresh = sha1.encode("quiet-river");
        assertEquals(9 + 20, Base64.getDecoder().decode(fresh).length, "SHA-1 derives a 160-bit key");
        assertTrue(sha1.matches("quiet-river", fresh));
        assertTrue(sha1.matches("quiet-river", DjangoHash.parse(DJANGO_RIVER).springEncoded()));
    }

    @Test
    void refusesMalformedRows() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("gwen", "pbkdf2_sha256$36000$seasalt2$not*base64*at*all");
        rows.put("hugo", "bcrypt_sha256$$2b$12$abcdefghijklmnopqrstuv");
        rows.put("ines", "pbkdf2_sha256$many$seasalt2$QkIBVCvGmTmyjPJ5yox2y/jQB8isvgUNK98FxOU1UYo=");
        rows.put("jonas", UserTable.NATIVE_PREFIX + "zz" + "00".repeat(79));
        rows.put("karin", UserTable.NATIVE_PREFIX + "abc");
        rows.put("lars", DJANGO_LETMEIN);

        UserTable table = new UserTable(PEPPER);
        rows.forEach(table::importRow);

        for (String user : rows.keySet()) {
            String password = user.equals("lars") ? "l\u00e8tmein" : "anything";
            assertEquals(user.equals("lars"), table.login(user, password), user);
        }
        assertEquals(List.of("gwen", "hugo", "ines", "jonas", "karin"), table.rejectedUsers());
        assertEquals(rows.get("gwen"), table.row("gwen"), "a rejected row is kept for an operator");
        assertTrue(table.row("lars").startsWith(UserTable.NATIVE_PREFIX));
    }

    private static Pbkdf2PasswordEncoder base64Encoder(
            String secret, int saltLength, int iterations, SecretKeyFactoryAlgorithm algorithm) {
        Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(secret, saltLength, iterations, algorithm);
        encoder.setEncodeHashAsBase64(true);
        return encoder;
    }

    /// One `pbkdf2_<digest>$<iterations>$<salt>$<base64 key>` row as Django writes it.
    private record DjangoHash(SecretKeyFactoryAlgorithm algorithm, int iterations, String salt, String key) {

        static DjangoHash parse(String row) {
            String[] parts = row.split("\\$", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Not a Django PBKDF2 hash");
            }
            SecretKeyFactoryAlgorithm algorithm = switch (parts[0]) {
                case "pbkdf2_sha256" -> SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;
                case "pbkdf2_sha1" -> SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA1;
                default -> throw new IllegalArgumentException("Unsupported Django hasher " + parts[0]);
            };
            return new DjangoHash(algorithm, Integer.parseInt(parts[1]), parts[2], parts[3]);
        }

        /// Django salts with the UTF-8 bytes of the salt string and adds no secret.
        Pbkdf2PasswordEncoder encoder() {
            return base64Encoder("", Utf8.encode(this.salt).length, this.iterations, this.algorithm);
        }

        /// Spring stores the salt followed by the derived key in one encoded value.
        String springEncoded() {
            byte[] derivedKey = Base64.getDecoder().decode(this.key);
            return Base64.getEncoder().encodeToString(EncodingUtils.concatenate(Utf8.encode(this.salt), derivedKey));
        }
    }

    /// The migrated user table: Django rows until a user logs in, peppered SHA-512 rows after.
    private static final class UserTable {

        static final String NATIVE_PREFIX = "pbkdf2-sha512$";

        private final Map<String, String> rows = new LinkedHashMap<>();
        private final List<String> rejected = new ArrayList<>();
        private final Pbkdf2PasswordEncoder current;

        UserTable(String pepper) {
            this.current = new Pbkdf2PasswordEncoder(
                    pepper, 16, 40_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA512);
        }

        void importRow(String user, String hash) {
            this.rows.put(user, hash);
        }

        String row(String user) {
            return this.rows.get(user);
        }

        List<String> rejectedUsers() {
            return this.rejected;
        }

        boolean login(String user, String password) {
            String row = this.rows.get(user);
            if (row == null) {
                return false;
            }
            try {
                if (row.startsWith(NATIVE_PREFIX)) {
                    return this.current.matches(password, row.substring(NATIVE_PREFIX.length()));
                }
                DjangoHash django = DjangoHash.parse(row);
                if (!django.encoder().matches(password, django.springEncoded())) {
                    return false;
                }
                this.rows.put(user, NATIVE_PREFIX + this.current.encode(password));
                return true;
            } catch (IllegalArgumentException ex) {
                this.rejected.add(user);
                return false;
            }
        }
    }
}
