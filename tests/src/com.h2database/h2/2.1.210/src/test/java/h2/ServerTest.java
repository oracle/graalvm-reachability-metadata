/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.engine.SysProperties;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerTest {
    @Test
    void openBrowserProbesDesktopSupportWhenNoBrowserIsConfigured() {
        String originalBrowser = System.getProperty(SysProperties.H2_BROWSER);
        String originalHeadless = System.getProperty("java.awt.headless");
        System.clearProperty(SysProperties.H2_BROWSER);
        System.setProperty("java.awt.headless", "true");
        try {
            try {
                Server.openBrowser("http://127.0.0.1/");
            } catch (Exception exception) {
                assertThat(exception).hasMessageContaining("Failed to start a browser");
            }
        } finally {
            restoreProperty(SysProperties.H2_BROWSER, originalBrowser);
            restoreProperty("java.awt.headless", originalHeadless);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
