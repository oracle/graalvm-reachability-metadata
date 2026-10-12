/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.videoinput;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.ClassProperties;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.tools.InfoMap;
import org.bytedeco.videoinput.presets.videoInputLib;

public class VideoinputTest {
    @Test
    void loadsWindowsBindingConfiguration() {
        Properties windows = Loader.loadProperties("windows-x86_64", null);

        ClassProperties binding =
                Loader.loadProperties(videoInputLib.class, windows, true);

        assertThat(binding.isLoaded()).isTrue();
        assertThat(binding.get("target")).contains("org.bytedeco.videoinput");
        assertThat(binding.get("global"))
                .contains("org.bytedeco.videoinput.global.videoInputLib");
        assertThat(binding.get("platform.include"))
                .contains("<videoInput.h>", "<videoInput.cpp>");
        assertThat(binding.get("platform.link"))
                .contains("ole32", "oleaut32", "amstrmid", "strmiids", "uuid");
    }

    @Test
    void appliesDirectShowConfigurationOnlyToWindows() {
        Properties windows = Loader.loadProperties("windows-x86_64", null);
        Properties linux = Loader.loadProperties("linux-x86_64", null);

        assertThat(Loader.checkPlatform(videoInputLib.class, windows)).isTrue();
        assertThat(Loader.checkPlatform(videoInputLib.class, linux)).isFalse();
    }

    @Test
    void mapsNativeHeaderTypesForBindingGeneration() {
        InfoMap mappings = new InfoMap();

        new videoInputLib().map(mappings);

        assertThat(mappings.containsKey("videoInput.cpp")).isTrue();
        assertThat(mappings.containsKey("_WIN32_WINNT")).isTrue();
        assertThat(mappings.containsKey("std::vector<std::string>")).isTrue();
        assertThat(mappings.containsKey("long")).isTrue();
    }

    @Test
    void readsPackagedBindingVersion() throws Exception {
        String version =
                Loader.getVersion("org.bytedeco", "videoinput", videoInputLib.class);

        assertThat(version).isNotBlank();
    }
}
