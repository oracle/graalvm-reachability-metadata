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
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.password.AbstractPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.util.EncodingUtils;

/**
 * Application-defined password hashing schemes built on the `AbstractPasswordEncoder`
 * template, which generates the salt, hex-frames `salt || hash`, and compares in constant time.
 *
 * A loyalty-programme backend replaces a legacy service that stored every member password as
 * hex `salt || HMAC-SHA256(pepper, salt || password)` with an eight byte salt. The new
 * backend implements that scheme as an `AbstractPasswordEncoder` so the imported rows keep
 * verifying, registers it beside bcrypt in a `DelegatingPasswordEncoder` that also accepts the
 * untagged legacy rows, and rehashes each member to bcrypt on their next successful login.
 */
public class LoyaltyBackendHmacPasswordTest {

    private static final byte[] PEPPER = "loyalty-pepper-2019".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ROTATED_PEPPER = "loyalty-pepper-2024".getBytes(StandardCharsets.US_ASCII);

    @Test
    void verifiesRowsTheLegacyServiceWrote() {
        Map<String, String> imported = new LinkedHashMap<>();
        imported.put("member-1001", LegacyService.row("tangerine-kite", new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
        imported.put("member-1002", LegacyService.row("basalt-orbit", new byte[] {-1, -2, -3, -4, -5, -6, -7, -8}));
        imported.put("member-1003",
                LegacyService.row("juniper-wharf", new byte[] {0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x7f})
                        .toUpperCase());
        Map<String, String> passwords = Map.of(
                "member-1001", "tangerine-kite",
                "member-1002", "basalt-orbit",
                "member-1003", "juniper-wharf");

        PasswordEncoder legacy = new HmacSha256PasswordEncoder(PEPPER);
        for (Map.Entry<String, String> row : imported.entrySet()) {
            String password = passwords.get(row.getKey());
            assertTrue(legacy.matches(password, row.getValue()), row.getKey());
            assertFalse(legacy.matches(password + " ", row.getValue()), row.getKey());
            assertFalse(legacy.matches(password.substring(1), row.getValue()), row.getKey());
        }

        PasswordEncoder rotated = new HmacSha256PasswordEncoder(ROTATED_PEPPER);
        assertFalse(rotated.matches("tangerine-kite", imported.get("member-1001")),
                "a row only verifies under the pepper it was written with");
    }

    @Test
    void writesRowsInTheLegacyLayout() {
        PasswordEncoder legacy = new HmacSha256PasswordEncoder(PEPPER);

        String first = legacy.encode("saffron-dune");
        String second = legacy.encode("saffron-dune");

        assertEquals(2 * (8 + 32), first.length(), "hex of an eight byte salt and a SHA-256 MAC");
        assertTrue(first.matches("[0-9a-f]+"), first);
        assertNotEquals(first.substring(0, 16), second.substring(0, 16), "every row draws a fresh salt");
        assertNotEquals(first, second);

        byte[] decoded = HexFormat.of().parseHex(first);
        byte[] salt = Arrays.copyOf(decoded, 8);
        assertEquals(first, LegacyService.row("saffron-dune", salt), "the legacy service reads the row back");
        assertArrayEquals(LegacyService.mac("saffron-dune", salt), Arrays.copyOfRange(decoded, 8, decoded.length));

        assertTrue(legacy.matches("saffron-dune", first));
        assertTrue(legacy.matches("saffron-dune", second));
        assertFalse(legacy.matches("saffron-dunes", first));
        assertFalse(legacy.upgradeEncoding(first), "the template never asks for a rehash on its own");
    }

    @Test
    void rehashesMembersToBcryptOnLogin() {
        MemberStore store = new MemberStore(migratingEncoder());
        store.importRow("member-2001", LegacyService.row("cobalt-lantern", new byte[] {9, 9, 9, 9, 1, 1, 1, 1}));
        store.importRow("member-2002",
                "{hmac}" + LegacyService.row("marble-fjord", new byte[] {3, 1, 4, 1, 5, 9, 2, 6}));

        assertFalse(store.login("member-2001", "cobalt-lanterns"));
        assertFalse(store.storedRow("member-2001").startsWith("{"), "a failed login leaves the row alone");

        assertTrue(store.login("member-2001", "cobalt-lantern"));
        assertTrue(store.login("member-2002", "marble-fjord"));
        for (String member : new String[] {"member-2001", "member-2002"}) {
            String row = store.storedRow(member);
            assertTrue(row.startsWith("{bcrypt}$2a$"), row);
        }
        assertTrue(store.login("member-2001", "cobalt-lantern"), "the bcrypt row verifies the same password");
        assertFalse(store.login("member-2002", "cobalt-lantern"));
        assertEquals(2, store.rehashCount(), "a bcrypt row is never rehashed again");

        store.register("member-2003", "quartz-meadow");
        assertTrue(store.storedRow("member-2003").startsWith("{bcrypt}"), "new members go straight to bcrypt");
        assertTrue(store.login("member-2003", "quartz-meadow"));
        assertEquals(2, store.rehashCount());
    }

    @Test
    void rejectsRowsThatAreNotHexFramed() {
        PasswordEncoder legacy = new HmacSha256PasswordEncoder(PEPPER);
        String valid = LegacyService.row("ember-canal", new byte[] {7, 6, 5, 4, 3, 2, 1, 0});

        assertThrows(IllegalArgumentException.class, () -> legacy.matches("ember-canal", valid.substring(1)),
                "an odd number of hex digits is a corrupt row");
        assertThrows(IllegalArgumentException.class,
                () -> legacy.matches("ember-canal", valid.substring(0, valid.length() - 2) + "zz"));

        MemberStore store = new MemberStore(migratingEncoder());
        store.importRow("member-3001", "{md5}5f4dcc3b5aa765d61d8327deb882cf99");
        assertThrows(IllegalArgumentException.class, () -> store.login("member-3001", "password"),
                "an unregistered scheme falls through to the legacy encoder, which rejects it");
    }

    /// Builds the encoder the backend wires: bcrypt for new rows, the legacy scheme for old ones.
    private static PasswordEncoder migratingEncoder() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(4));
        encoders.put("hmac", new HmacSha256PasswordEncoder(PEPPER));
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("bcrypt", encoders);
        delegating.setDefaultPasswordEncoderForMatches(encoders.get("hmac"));
        return delegating;
    }

    /// The legacy scheme: HMAC-SHA256 under a server-side pepper over the salt and the password.
    private static final class HmacSha256PasswordEncoder extends AbstractPasswordEncoder {
        private final SecretKeySpec pepper;

        HmacSha256PasswordEncoder(byte[] pepper) {
            this.pepper = new SecretKeySpec(pepper, "HmacSHA256");
        }

        @Override
        protected byte[] encode(CharSequence rawPassword, byte[] salt) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(pepper);
                return mac.doFinal(EncodingUtils.concatenate(salt, Utf8.encode(rawPassword)));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("HmacSHA256 is unavailable", e);
            }
        }
    }

    /// The member table, rehashing a row whenever the encoder reports it outdated.
    private static final class MemberStore {
        private final PasswordEncoder encoder;
        private final Map<String, String> rows = new LinkedHashMap<>();
        private int rehashes;

        MemberStore(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        void importRow(String member, String row) {
            rows.put(member, row);
        }

        void register(String member, String rawPassword) {
            rows.put(member, encoder.encode(rawPassword));
        }

        boolean login(String member, String rawPassword) {
            String row = rows.get(member);
            if (row == null || !encoder.matches(rawPassword, row)) {
                return false;
            }
            if (encoder.upgradeEncoding(row)) {
                rows.put(member, encoder.encode(rawPassword));
                rehashes++;
            }
            return true;
        }

        String storedRow(String member) {
            return rows.get(member);
        }

        int rehashCount() {
            return rehashes;
        }
    }

    /// Recomputes rows the way the retired service did, independently of Spring Security.
    private static final class LegacyService {
        static String row(String password, byte[] salt) {
            byte[] mac = mac(password, salt);
            byte[] framed = Arrays.copyOf(salt, salt.length + mac.length);
            System.arraycopy(mac, 0, framed, salt.length, mac.length);
            return HexFormat.of().formatHex(framed);
        }

        static byte[] mac(String password, byte[] salt) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(PEPPER, "HmacSHA256"));
                mac.update(salt);
                return mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
