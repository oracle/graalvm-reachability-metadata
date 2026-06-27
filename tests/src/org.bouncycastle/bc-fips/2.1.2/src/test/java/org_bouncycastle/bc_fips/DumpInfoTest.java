/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bouncycastle.crypto.fips.FipsOperationError;
import org.bouncycastle.util.DumpInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(2)
public class DumpInfoTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void infoStringRunsModuleClassInitializationChecks() {
        try {
            String info = DumpInfo.getInfoString(false);

            assertTrue(info.contains("Version Info:"));
            assertTrue(info.contains("FIPS Ready Status:"));
            assertTrue(info.contains("Module SHA-256 HMAC:"));
        } catch (FipsOperationError error) {
            assertTrue(isChecksumFailure(error), error.getMessage());
        }
    }

    private static boolean isChecksumFailure(Error error) {
        return "org.bouncycastle.crypto.fips.FipsOperationError".equals(error.getClass().getName())
                && error.getMessage() != null
                && error.getMessage().startsWith("Module checksum failed");
    }
}
