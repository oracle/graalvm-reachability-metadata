/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.tools.Upgrade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeTest {
    private static final int UPGRADE_SOURCE_BUILD_ID = 200;
    private static final String UPGRADE_SOURCE_VERSION = "1.4.200";
    private static final String UPGRADE_JAR = "h2-" + UPGRADE_SOURCE_VERSION + ".jar";
    private static final String UPGRADE_JAR_RESOURCE = "h2-upgrade/" + UPGRADE_JAR;

    @Test
    void loadsAndUnloadsPreviousH2DriverWithIsolatedClassLoader() throws Exception {
        try {
            cacheUpgradeSourceJar();

            Driver driver = Upgrade.loadH2(UPGRADE_SOURCE_BUILD_ID);
            try {
                assertThat(driver).isNotNull();
                assertThat(driver.getClass().getName()).isEqualTo("org.h2.Driver");
                assertThat(driver.acceptsURL("jdbc:h2:mem:upgrade-test")).isTrue();
            } finally {
                Upgrade.unloadH2(driver);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void cacheUpgradeSourceJar() throws IOException {
        Path target = Paths.get(System.getProperty("user.home"), ".m2", "repository", "com", "h2database", "h2",
                UPGRADE_SOURCE_VERSION, UPGRADE_JAR);
        Files.createDirectories(target.getParent());
        try (InputStream input = UpgradeTest.class.getClassLoader().getResourceAsStream(UPGRADE_JAR_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + UPGRADE_JAR_RESOURCE);
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
