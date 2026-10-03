/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.djl.modality.audio.AudioFactory;
import ai.djl.util.ClassLoaderUtils;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class ClassLoaderUtilsTest {
    @Test
    void initializesNamedImplementationAndEnumeratesResources() throws IOException {
        AudioFactory factory = ClassLoaderUtils.initClass(
                ClassLoaderUtils.getContextClassLoader(),
                AudioFactory.class,
                "ai.djl.modality.audio.SampledAudioFactory");

        assertThat(factory).isInstanceOf(AudioFactory.class);

        Enumeration<URL> resources = ClassLoaderUtils.getResources("djl-test-model.txt");
        assertThat(resources.hasMoreElements()).isTrue();
    }

    @Test
    void reportsInvalidNativeHelper() {
        assertThatThrownBy(() -> ClassLoaderUtils.nativeLoad("java.lang.System", "/missing/native/library"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
