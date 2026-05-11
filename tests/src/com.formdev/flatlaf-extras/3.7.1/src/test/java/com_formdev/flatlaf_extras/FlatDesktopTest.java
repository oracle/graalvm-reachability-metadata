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
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.EventQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("com.formdev.flatlaf.util.SystemInfo")
public class FlatDesktopTest {
    @Test
    void installsLegacyMacDesktopHandlersThroughPublicApi() throws Exception {
        Application.resetHandlers();

        try (SystemInfoOverride ignored = SystemInfoOverride.legacyMacJava8()) {
            boolean[] aboutInvoked = new boolean[1];
            boolean[] quitInvoked = new boolean[1];

            assertLegacyMacJava8();
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
            Application.resetHandlers();
        }
    }

    private static void assertLegacyMacJava8() {
        assertThat(SystemInfo.isMacOS).isTrue();
        assertThat(SystemInfo.isJava_9_orLater).isFalse();
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
