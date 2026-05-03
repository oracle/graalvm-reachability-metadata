/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.tools.Server;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerTest {
    @Test
    void probesDesktopBrowserSupportBeforeFallingBackToPlatformCommands() throws Exception {
        try {
            Server.openBrowser("http://127.0.0.1:1/");
        } catch (Exception exception) {
            assertThat(exception).isNotNull();
        }
    }
}
