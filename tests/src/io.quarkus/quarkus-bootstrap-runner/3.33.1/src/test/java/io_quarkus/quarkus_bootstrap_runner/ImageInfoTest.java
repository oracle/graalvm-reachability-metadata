/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.bootstrap.graal.ImageInfo;
import org.junit.jupiter.api.Test;

public class ImageInfoTest {
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    @Test
    void imageCodeHelpersReflectCurrentSystemPropertyValue() {
        String originalImageCode = System.getProperty(IMAGE_CODE_PROPERTY);
        try {
            System.setProperty(IMAGE_CODE_PROPERTY, "runtime");
            assertThat(ImageInfo.inImageRuntimeCode()).isTrue();
            assertThat(ImageInfo.inImageBuildtimeCode()).isFalse();

            System.setProperty(IMAGE_CODE_PROPERTY, "buildtime");
            assertThat(ImageInfo.inImageBuildtimeCode()).isTrue();
            assertThat(ImageInfo.inImageRuntimeCode()).isFalse();

            System.setProperty(IMAGE_CODE_PROPERTY, "unknown");
            assertThat(ImageInfo.inImageRuntimeCode()).isFalse();
            assertThat(ImageInfo.inImageBuildtimeCode()).isFalse();

            System.clearProperty(IMAGE_CODE_PROPERTY);
            assertThat(ImageInfo.inImageRuntimeCode()).isFalse();
            assertThat(ImageInfo.inImageBuildtimeCode()).isFalse();
        } finally {
            restoreImageCode(originalImageCode);
        }
    }

    private static void restoreImageCode(String originalImageCode) {
        if (originalImageCode == null) {
            System.clearProperty(IMAGE_CODE_PROPERTY);
        } else {
            System.setProperty(IMAGE_CODE_PROPERTY, originalImageCode);
        }
    }
}
