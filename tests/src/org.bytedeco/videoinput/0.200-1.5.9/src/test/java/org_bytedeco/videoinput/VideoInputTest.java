/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.videoinput;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.tools.Generator;
import org.bytedeco.javacpp.tools.Logger;
import org.bytedeco.videoinput.videoInput;

public class VideoInputTest {
    @TempDir
    Path outputDirectory;

    @Test
    void generatesJniBridgeForByteArrayFrameCapture() throws Exception {
        Path source = outputDirectory.resolve("videoinput.cpp");
        Properties windows = Loader.loadProperties("windows-x86_64", null);
        Generator generator = new Generator(Logger.create(VideoInputTest.class), windows);

        boolean generated = generator.generate(
                source.toString(), null, null, null, "", null, null, videoInput.class);

        assertThat(generated).isTrue();
        assertThat(source).exists();
        assertThat(Files.readString(source))
                .contains(
                        "Java_org_bytedeco_videoinput_videoInput_getPixels__I_3B",
                        "ptr->getPixels",
                        "GetByteArrayElements",
                        "ReleaseByteArrayElements");
    }
}
