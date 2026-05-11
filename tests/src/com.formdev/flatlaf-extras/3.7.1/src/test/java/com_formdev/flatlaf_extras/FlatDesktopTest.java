/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import com.apple.eawt.Application;
import com.apple.eawt.QuitResponse;
import com.formdev.flatlaf.extras.FlatDesktop;
import java.awt.EventQueue;
import org.junit.jupiter.api.Test;

public class FlatDesktopTest {
    @Test
    void installsLegacyMacDesktopHandlersThroughPublicApi() throws Exception {
        String originalOsName = System.getProperty("os.name");
        String originalJavaVersion = System.getProperty("java.version");
        Application.resetHandlers();

        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("java.version", "1.8.0_392");

            boolean[] aboutInvoked = new boolean[1];
            boolean[] quitInvoked = new boolean[1];

            FlatDesktop.setAboutHandler(() -> aboutInvoked[0] = true);
            FlatDesktop.setQuitHandler(response -> quitInvoked[0] = true);

            Application application = Application.getApplication();

            assertThat(application.hasAboutHandler()).isTrue();
            assertThat(application.hasQuitHandler()).isTrue();
            assertThat(application.dispatchAbout()).isTrue();
            EventQueue.invokeAndWait(() -> { });
            assertThat(application.dispatchQuitRequest(new NoOpQuitResponse())).isTrue();
            assertThat(aboutInvoked[0]).isTrue();
            assertThat(quitInvoked[0]).isTrue();
        } finally {
            restoreProperty("os.name", originalOsName);
            restoreProperty("java.version", originalJavaVersion);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    private static final class NoOpQuitResponse implements QuitResponse {
        @Override
        public void performQuit() {
        }

        @Override
        public void cancelQuit() {
        }
    }
}
