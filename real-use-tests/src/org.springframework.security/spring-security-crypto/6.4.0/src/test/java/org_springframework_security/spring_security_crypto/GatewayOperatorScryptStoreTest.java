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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.generators.SCrypt;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

/**
 * Scrypt password hashing through `SCryptPasswordEncoder` configured with an explicit CPU cost,
 * block size, parallelization, key length, and salt length.
 *
 * The admin console of an edge gateway stores operator credentials as scrypt rows. A fleet
 * provisioning tool on another platform writes rows for new gateways, the console writes rows
 * whose header carries the cost parameters, rehashes rows below the fleet cost policy on login,
 * refuses rows it cannot read, and rejects cost settings that scrypt does not allow.
 */
public class GatewayOperatorScryptStoreTest {

    /// Fleet policy: N = 2^15, r = 8, p = 1, a 32-byte key and a 16-byte salt.
    private static final int POLICY_CPU_COST = 32768;
    private static final int POLICY_BLOCK_SIZE = 8;
    private static final int POLICY_PARALLELIZATION = 1;
    private static final int KEY_LENGTH = 32;
    private static final int SALT_LENGTH = 16;

    /// Header of a row written under the policy: log2(N) = 15, r = 8, p = 1.
    private static final String POLICY_HEADER = "$f0801$";

    @Test
    void verifiesRowsProvisionedByAnotherScryptImplementation() {
        byte[] salt = "gw-0042-site-ams".getBytes(StandardCharsets.US_ASCII);
        byte[] key = OtherPlatform.scrypt("ops-console-7781", salt, 16384, 8, 2, KEY_LENGTH);
        String provisioned = "$e0802$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(key);

        OperatorStore operators = new OperatorStore(policyEncoder());
        operators.importRows(Map.of("ana@fleet.example", provisioned));

        assertTrue(operators.login("ana@fleet.example", "ops-console-7781"));
        assertFalse(operators.login("ana@fleet.example", "ops-console-7782"));
        assertFalse(operators.login("bo@fleet.example", "ops-console-7781"), "unknown operators never log in");
    }

    @Test
    void verifiesTheRfc7914ReferenceVectorWithAWideKey() {
        byte[] reference = HexFormat.of().parseHex(
                "fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162"
                        + "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640");
        String row = "$a0810$" + Base64.getEncoder().encodeToString("NaCl".getBytes(StandardCharsets.US_ASCII))
                + "$" + Base64.getEncoder().encodeToString(reference);

        SCryptPasswordEncoder wideKeys = new SCryptPasswordEncoder(POLICY_CPU_COST, 8, 1, 64, SALT_LENGTH);
        assertTrue(wideKeys.matches("password", row), "N = 1024, r = 8, p = 16 are read from the row header");
        assertFalse(wideKeys.matches("Password", row));
        assertFalse(policyEncoder().matches("password", row), "the key length comes from the encoder");
        assertTrue(wideKeys.upgradeEncoding(row), "N = 1024 is below the policy");
    }

    @Test
    void writesRowsWhoseHeaderCarriesTheCostParameters() {
        OperatorStore operators = new OperatorStore(policyEncoder());
        String password = "Gr\u00fc\u00dfe aus der Leitwarte \u7ba1\u7406";

        operators.register("carl@fleet.example", password);
        operators.register("dana@fleet.example", password);

        String carl = operators.storedHash("carl@fleet.example");
        String dana = operators.storedHash("dana@fleet.example");
        assertTrue(carl.startsWith(POLICY_HEADER), carl);
        assertNotEquals(carl, dana, "every row carries its own salt");

        String[] fields = carl.split("\\$");
        assertEquals(4, fields.length, carl);
        assertEquals(15L << 16 | 8 << 8 | 1, Long.parseLong(fields[1], 16));
        byte[] salt = Base64.getDecoder().decode(fields[2]);
        byte[] key = Base64.getDecoder().decode(fields[3]);
        assertEquals(SALT_LENGTH, salt.length);
        assertEquals(KEY_LENGTH, key.length);

        byte[] recomputed = OtherPlatform.scrypt(password, salt, POLICY_CPU_COST, 8, 1, KEY_LENGTH);
        assertArrayEquals(key, recomputed, "another platform derives the same key from the UTF-8 password");

        assertTrue(operators.login("carl@fleet.example", password));
        assertFalse(operators.login("carl@fleet.example", "Grusse aus der Leitwarte \u7ba1\u7406"));
        assertEquals(carl, operators.storedHash("carl@fleet.example"), "a row at the policy is not rewritten");
        assertEquals(List.of(), operators.rehashed());
    }

    @Test
    void rehashesRowsBelowTheFleetPolicyOnLogin() {
        SCryptPasswordEncoder policy = policyEncoder();
        OperatorStore operators = new OperatorStore(policy);
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("emil@fleet.example", SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1().encode("north-dock"));
        rows.put("fay@fleet.example",
                new SCryptPasswordEncoder(POLICY_CPU_COST, 4, 1, KEY_LENGTH, SALT_LENGTH).encode("south-pier"));
        rows.put("gus@fleet.example", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8().encode("east-gate"));
        rows.put("hana@fleet.example",
                new SCryptPasswordEncoder(POLICY_CPU_COST, 8, 2, KEY_LENGTH, SALT_LENGTH).encode("west-yard"));
        operators.importRows(rows);

        assertTrue(rows.get("emil@fleet.example").startsWith("$e0801$"));
        assertTrue(rows.get("fay@fleet.example").startsWith("$f0401$"));
        assertTrue(rows.get("gus@fleet.example").startsWith("$100801$"));
        assertTrue(rows.get("hana@fleet.example").startsWith("$f0802$"));
        assertTrue(policy.upgradeEncoding(rows.get("emil@fleet.example")), "CPU cost below the policy");
        assertTrue(policy.upgradeEncoding(rows.get("fay@fleet.example")), "block size below the policy");
        assertFalse(policy.upgradeEncoding(rows.get("gus@fleet.example")), "CPU cost above the policy");
        assertFalse(policy.upgradeEncoding(rows.get("hana@fleet.example")), "parallelization above the policy");

        assertFalse(operators.login("emil@fleet.example", "north-docks"));
        assertEquals(rows.get("emil@fleet.example"), operators.storedHash("emil@fleet.example"),
                "a failed login never rewrites the row");

        assertTrue(operators.login("emil@fleet.example", "north-dock"));
        assertTrue(operators.login("fay@fleet.example", "south-pier"));
        assertTrue(operators.login("gus@fleet.example", "east-gate"));
        assertTrue(operators.login("hana@fleet.example", "west-yard"));
        assertEquals(List.of("emil@fleet.example", "fay@fleet.example"), operators.rehashed());

        for (String email : List.of("emil@fleet.example", "fay@fleet.example")) {
            String upgraded = operators.storedHash(email);
            assertTrue(upgraded.startsWith(POLICY_HEADER), upgraded);
            assertFalse(policy.upgradeEncoding(upgraded), email);
        }
        assertTrue(operators.login("emil@fleet.example", "north-dock"), "the rehashed row still verifies");
        assertEquals(rows.get("gus@fleet.example"), operators.storedHash("gus@fleet.example"));
        assertEquals(2, operators.rehashed().size(), "an upgraded row is rehashed only once");
    }

    @Test
    void refusesRowsThatAreNotScryptHashes() {
        OperatorStore operators = new OperatorStore(policyEncoder());
        String valid = policyEncoder().encode("password");
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("ivan@fleet.example", "");
        rows.put("jill@fleet.example", "$f0801$c29tZXNhbHQ=");
        rows.put("kurt@fleet.example", valid.substring(0, valid.lastIndexOf('$')) + "$" + valid);
        rows.put("lena@fleet.example", valid.replace(POLICY_HEADER, "$f0801$c2FsdA==$"));
        operators.importRows(rows);

        for (String email : rows.keySet()) {
            assertFalse(operators.login(email, "password"), email);
        }
        assertEquals(List.of(), operators.rehashed(), "unreadable rows are never rehashed");
        assertEquals(rows.get("jill@fleet.example"), operators.storedHash("jill@fleet.example"));

        SCryptPasswordEncoder policy = policyEncoder();
        assertFalse(policy.upgradeEncoding(null));
        assertFalse(policy.upgradeEncoding(""));
        assertThrows(IllegalArgumentException.class, () -> policy.upgradeEncoding("$f0801$c29tZXNhbHQ="));
    }

    @Test
    void rejectsCostSettingsThatScryptDoesNotAllow() {
        IllegalArgumentException cpu = assertThrows(IllegalArgumentException.class,
                () -> new SCryptPasswordEncoder(1, 8, 1, KEY_LENGTH, SALT_LENGTH));
        assertEquals("Cpu cost parameter must be > 1.", cpu.getMessage());

        IllegalArgumentException memory = assertThrows(IllegalArgumentException.class,
                () -> new SCryptPasswordEncoder(POLICY_CPU_COST, 0, 1, KEY_LENGTH, SALT_LENGTH));
        assertEquals("Memory cost must be >= 1.", memory.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> new SCryptPasswordEncoder(POLICY_CPU_COST, 8, 0, KEY_LENGTH, SALT_LENGTH));
        assertThrows(IllegalArgumentException.class,
                () -> new SCryptPasswordEncoder(POLICY_CPU_COST, 8, 1, 0, SALT_LENGTH));
        assertThrows(IllegalArgumentException.class,
                () -> new SCryptPasswordEncoder(POLICY_CPU_COST, 8, 1, KEY_LENGTH, 0));
    }

    private static SCryptPasswordEncoder policyEncoder() {
        return new SCryptPasswordEncoder(POLICY_CPU_COST, POLICY_BLOCK_SIZE, POLICY_PARALLELIZATION,
                KEY_LENGTH, SALT_LENGTH);
    }

    /// The gateway's operator table: verifies logins and rehashes rows below the fleet policy.
    private static final class OperatorStore {
        private final PasswordEncoder encoder;
        private final Map<String, String> rows = new LinkedHashMap<>();
        private final List<String> rehashed = new ArrayList<>();

        OperatorStore(PasswordEncoder encoder) {
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

    /// Derives scrypt keys the way another platform's scrypt library does.
    private static final class OtherPlatform {

        static byte[] scrypt(String password, byte[] salt, int cpuCost, int blockSize, int lanes, int length) {
            return SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, cpuCost, blockSize, lanes, length);
        }
    }
}
