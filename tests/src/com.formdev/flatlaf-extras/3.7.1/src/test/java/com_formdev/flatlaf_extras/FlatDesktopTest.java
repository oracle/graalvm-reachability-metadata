/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import com.apple.eawt.Application;
import com.formdev.flatlaf.extras.FlatDesktop;
import org.junit.jupiter.api.Test;

public class FlatDesktopTest {
    @Test
    void installsLegacyMacDesktopHandlersThroughPublicApi() {
        String originalOsName = System.getProperty("os.name");
        String originalJavaVersion = System.getProperty("java.version");
        Application.resetHandlers();

        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("java.version", "1.8.0_392");

            FlatDesktop.setAboutHandler(() -> { });
            FlatDesktop.setQuitHandler(response -> { });

            assertThat(Application.getApplication().hasAboutHandler()).isTrue();
            assertThat(Application.getApplication().hasQuitHandler()).isTrue();
        } finally {
            restoreProperty("os.name", originalOsName);
            restoreProperty("java.version", originalJavaVersion);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value != null)
            System.setProperty(key, value);
        else
            System.clearProperty(key);
    }
}
