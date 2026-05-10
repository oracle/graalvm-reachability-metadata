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
import org.junit.jupiter.api.Test;

public class FlatDesktopAnonymous1Test {
    @Test
    void delegatesPerformAndCancelQuitThroughLegacyMacQuitResponse() {
        String originalOsName = System.getProperty("os.name");
        String originalJavaVersion = System.getProperty("java.version");
        Application.resetHandlers();

        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("java.version", "1.8.0_392");

            FlatDesktop.setQuitHandler(response -> {
                response.performQuit();
                response.cancelQuit();
            });

            CountingQuitResponse nativeResponse = new CountingQuitResponse();
            boolean dispatched = Application.getApplication().dispatchQuitRequest(nativeResponse);

            if (dispatched) {
                assertThat(nativeResponse.performQuitCount).isEqualTo(1);
                assertThat(nativeResponse.cancelQuitCount).isEqualTo(1);
            } else {
                assertThat(nativeResponse.performQuitCount).isZero();
                assertThat(nativeResponse.cancelQuitCount).isZero();
            }
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

    private static final class CountingQuitResponse implements QuitResponse {
        private int performQuitCount;
        private int cancelQuitCount;

        @Override
        public void performQuit() {
            performQuitCount++;
        }

        @Override
        public void cancelQuit() {
            cancelQuitCount++;
        }
    }
}
