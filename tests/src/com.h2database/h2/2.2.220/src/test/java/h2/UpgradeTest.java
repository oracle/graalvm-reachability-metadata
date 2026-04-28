/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.Driver;
import org.h2.tools.Upgrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeTest {
    private static final int H2_VERSION_WITHOUT_CHECKSUM = 218;
    private static final String H2_VERSION_WITHOUT_CHECKSUM_TEXT = "2.1." + H2_VERSION_WITHOUT_CHECKSUM;

    @TempDir
    Path userHome;

    @Test
    void loadAndUnloadH2DriverThroughUpgradeTool() throws Exception {
        Path h2Jar = userHome.resolve(Path.of(".m2", "repository", "com", "h2database", "h2",
                H2_VERSION_WITHOUT_CHECKSUM_TEXT, "h2-" + H2_VERSION_WITHOUT_CHECKSUM_TEXT + ".jar"));
        Files.createDirectories(h2Jar.getParent());
        writeMinimalH2Jar(h2Jar);

        String previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
        java.sql.Driver driver = null;
        try {
            driver = Upgrade.loadH2(H2_VERSION_WITHOUT_CHECKSUM);

            assertThat(driver).isInstanceOf(Driver.class);
        } finally {
            if (driver != null) {
                Upgrade.unloadH2(driver);
                Driver.load();
            }
            if (previousUserHome != null) {
                System.setProperty("user.home", previousUserHome);
            } else {
                System.clearProperty("user.home");
            }
        }

        assertThat(DriverManager.getDriver("jdbc:h2:mem:upgrade-test")).isInstanceOf(Driver.class);
    }

    private static void writeMinimalH2Jar(Path h2Jar) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(h2Jar))) {
            jar.putNextEntry(new JarEntry("placeholder.txt"));
            jar.write("fallback to runtime H2 driver".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }
}
