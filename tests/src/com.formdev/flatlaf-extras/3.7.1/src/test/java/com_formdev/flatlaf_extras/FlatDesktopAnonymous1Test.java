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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("com.formdev.flatlaf.util.SystemInfo")
public class FlatDesktopAnonymous1Test {
    @Test
    void delegatesPerformAndCancelQuitThroughLegacyMacQuitResponse() throws Exception {
        Application.resetHandlers();

        try (SystemInfoOverride ignored = SystemInfoOverride.legacyMacJava8()) {
            assertLegacyMacJava8();
            FlatDesktop.setQuitHandler(response -> {
                response.performQuit();
                response.cancelQuit();
            });

            CountingQuitResponse nativeResponse = new CountingQuitResponse();
            boolean dispatched = Application.getApplication().dispatchQuitRequest(nativeResponse);

            assertThat(Application.getApplication().hasQuitHandler()).isTrue();
            assertThat(dispatched).isTrue();
            assertThat(nativeResponse.performQuitCount).isEqualTo(1);
            assertThat(nativeResponse.cancelQuitCount).isEqualTo(1);
        } finally {
            Application.resetHandlers();
        }
    }

    private static void assertLegacyMacJava8() {
        assertThat(SystemInfo.isMacOS).isTrue();
        assertThat(SystemInfo.isJava_9_orLater).isFalse();
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
