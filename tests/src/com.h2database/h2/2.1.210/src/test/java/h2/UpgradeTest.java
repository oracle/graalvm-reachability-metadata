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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeTest {
    private static final int LEGACY_H2_BUILD = 200;
    private static final String LEGACY_H2_RESOURCE = "/h2-upgrade/legacy-h2.jar";

    @Test
    void loadsAndUnloadsLegacyDriverWithUpgradeTool() throws Exception {
        assertDynamicClassLoading(() -> {
            Path temporaryHome = Files.createTempDirectory("h2-upgrade-home");
            installLegacyH2Jar(temporaryHome);
            String originalUserHome = System.getProperty("user.home");
            try {
                System.setProperty("user.home", temporaryHome.toString());
                Driver driver = Upgrade.loadH2(LEGACY_H2_BUILD);
                try {
                    assertThat(driver.acceptsURL("jdbc:h2:mem:upgrade-tool")).isTrue();
                    assertThat(driver.getMajorVersion()).isEqualTo(1);
                } finally {
                    Upgrade.unloadH2(driver);
                }
            } finally {
                restoreUserHome(originalUserHome);
            }
        });
    }

    private static void installLegacyH2Jar(Path temporaryHome) throws Exception {
        Path legacyJar = temporaryHome.resolve(".m2/repository/com/h2database/h2/1.4." + LEGACY_H2_BUILD)
                .resolve("h2-1.4." + LEGACY_H2_BUILD + ".jar");
        Files.createDirectories(legacyJar.getParent());
        try (InputStream inputStream = Objects.requireNonNull(UpgradeTest.class.getResourceAsStream(LEGACY_H2_RESOURCE),
                LEGACY_H2_RESOURCE + " not found")) {
            Files.copy(inputStream, legacyJar);
        }
    }

    private static void restoreUserHome(String originalUserHome) {
        if (originalUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private static void assertDynamicClassLoading(DynamicClassLoadingAssertion assertion) throws Exception {
        try {
            assertion.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private interface DynamicClassLoadingAssertion {
        void run() throws Exception;
    }
}
