/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.flycapture;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.flycapture.presets.FlyCapture2;
import org.bytedeco.javacpp.ClassProperties;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.tools.InfoMap;
import org.junit.jupiter.api.Test;

public class FlycaptureTest {
    @Test
    void configuresCppBinding() {
        ClassProperties properties =
                Loader.loadProperties(FlyCapture2.class, Loader.loadProperties(), false);
        InfoMap mappings = new InfoMap();

        new FlyCapture2().map(mappings);

        assertThat(properties.isLoaded()).isTrue();
        assertThat(properties.getProperty("target"))
                .isEqualTo("org.bytedeco.flycapture.FlyCapture2");
        assertThat(properties.getProperty("global"))
                .isEqualTo("org.bytedeco.flycapture.global.FlyCapture2");
        assertThat(properties.getProperty("platform.library")).isEqualTo("jniFlyCapture2");
        assertThat(properties.get("platform.link"))
                .anyMatch(link ->
                        link.startsWith("flycapture") || link.startsWith("FlyCapture2"));
        assertThat(mappings)
                .containsKeys(
                        "FLYCAPTURE2_API",
                        "MultiSyncLibrary::SyncManager",
                        "FlyCapture2::ImageEventCallback",
                        "FlyCapture2::CameraBase::GetRegisterString");
        assertThat(mappings.get("FlyCapture2::ImageEventCallback")).hasSize(1);
    }
}
