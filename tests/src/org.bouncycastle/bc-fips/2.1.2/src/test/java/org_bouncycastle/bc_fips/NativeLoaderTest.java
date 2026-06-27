/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.bouncycastle.crypto.CryptoServicesRegistrar;
import org.bouncycastle.crypto.NativeServices;
import org.bouncycastle.crypto.fips.FipsOperationError;
import org.bouncycastle.crypto.fips.FipsStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Order(0)
public class NativeLoaderTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String NATIVE_INSTALL_DIR_PROPERTY = "org.bouncycastle.native.loader.install_dir";
    private static final String MISSING_NATIVE_VARIANT = "coverage_missing_variant";

    private static String previousNativeVariant;
    private static String previousNativeInstallDirectory;

    @TempDir
    static Path nativeInstallDirectory;

    @BeforeAll
    static void requestMissingNativeVariant() {
        previousNativeVariant = System.getProperty(NATIVE_CPU_VARIANT_PROPERTY);
        previousNativeInstallDirectory = System.getProperty(NATIVE_INSTALL_DIR_PROPERTY);
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, MISSING_NATIVE_VARIANT);
        System.setProperty(NATIVE_INSTALL_DIR_PROPERTY, nativeInstallDirectory.toString());
    }

    @AfterAll
    static void restoreNativeLoaderProperties() {
        restoreProperty(NATIVE_CPU_VARIANT_PROPERTY, previousNativeVariant);
        restoreProperty(NATIVE_INSTALL_DIR_PROPERTY, previousNativeInstallDirectory);
    }

    @Test
    void fipsStartupAttemptsNativeProbeAndVariantDependencyResourceLookup() {
        try {
            assertTrue(FipsStatus.isReady());

            NativeServices nativeServices = CryptoServicesRegistrar.getNativeServices();
            assertEquals(MISSING_NATIVE_VARIANT, nativeServices.getVariant());
            assertFalse(nativeServices.isInstalled());
            assertFalse(nativeServices.isEnabled());
        } catch (FipsOperationError error) {
            assertTrue(isChecksumFailure(error), error.getMessage());
        }
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static boolean isChecksumFailure(Error error) {
        return "org.bouncycastle.crypto.fips.FipsOperationError".equals(error.getClass().getName())
                && error.getMessage() != null
                && error.getMessage().startsWith("Module checksum failed");
    }
}
