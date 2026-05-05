/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.tools.Upgrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeTest {
    private static final int LEGACY_BUILD_ID = 200;
    private static final String LEGACY_VERSION = "1.4." + LEGACY_BUILD_ID;
    private static final String LEGACY_JAR_PROPERTY = "h2.upgrade.oldJar";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAndUnloadsLegacyH2DriverThroughUpgradeTool() throws Exception {
        String previousUserHome = System.getProperty("user.home");
        Driver driver = null;
        try {
            System.setProperty("user.home", temporaryDirectory.toString());
            installLegacyJarInTemporaryMavenRepository();

            driver = Upgrade.loadH2(LEGACY_BUILD_ID);

            assertThat(driver.getMajorVersion()).isEqualTo(1);
            assertThat(driver.getMinorVersion()).isEqualTo(4);

            Driver loadedDriver = driver;
            driver = null;
            Upgrade.unloadH2(loadedDriver);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (ReflectiveOperationException exception) {
            if (!hasUnsupportedFeatureErrorCause(exception)) {
                throw exception;
            }
        } finally {
            if (driver != null) {
                Upgrade.unloadH2(driver);
            }
            restoreUserHome(previousUserHome);
        }
    }

    private void installLegacyJarInTemporaryMavenRepository() throws IOException {
        String legacyJar = System.getProperty(LEGACY_JAR_PROPERTY);
        assertThat(legacyJar).isNotBlank();

        Path targetJar = temporaryDirectory.resolve(".m2/repository/com/h2database/h2")
                .resolve(LEGACY_VERSION)
                .resolve("h2-" + LEGACY_VERSION + ".jar");
        Files.createDirectories(targetJar.getParent());
        Files.copy(Path.of(legacyJar), targetJar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean hasUnsupportedFeatureErrorCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void restoreUserHome(String previousUserHome) {
        if (previousUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", previousUserHome);
        }
    }
}
