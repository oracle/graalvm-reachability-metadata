/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import dalvik.system.CloseGuard;
import okhttp3.internal.platform.Platform;
import org.junit.jupiter.api.Test;

public class AndroidPlatformInnerCloseGuardTest {
    @Test
    void androidPlatformOpensAndWarnsCloseGuard() {
        Platform platform = Platform.get();

        Object stackTrace = platform.getStackTraceForCloseable("response.body().close()");
        platform.logCloseableLeak("leaked response", stackTrace);

        assertThat(stackTrace).isInstanceOf(CloseGuard.class);
        CloseGuard closeGuard = (CloseGuard) stackTrace;
        assertThat(closeGuard.closer()).isEqualTo("response.body().close()");
        assertThat(closeGuard.warned()).isTrue();
    }
}
