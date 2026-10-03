/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.util.Platform;
import org.junit.jupiter.api.Test;

public class PlatformTest {
    @Test
    void detectsPlatformWhenNoEngineBundleIsPresent() {
        Platform platform = Platform.detectPlatform("unconfigured-engine");

        assertThat(platform.isPlaceholder()).isTrue();
        assertThat(platform.getOsPrefix()).isNotBlank();
        assertThat(platform.getClassifier()).contains("-");
    }
}
