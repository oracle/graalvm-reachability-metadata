/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.opencv;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class OpencvTest {
    @Test
    void thresholdsGrayscaleImage() {
        Loader.load(opencv_java.class);
        Mat source = new Mat(2, 3, CvType.CV_8UC1);
        Mat thresholded = new Mat();

        try {
            byte[] pixels = {0, 64, 127, (byte) 128, (byte) 200, (byte) 255};
            assertThat(source.put(0, 0, pixels)).isEqualTo(pixels.length);

            double appliedThreshold =
                    Imgproc.threshold(source, thresholded, 127, 255, Imgproc.THRESH_BINARY);
            byte[] result = new byte[pixels.length];

            assertThat(thresholded.get(0, 0, result)).isEqualTo(result.length);
            assertThat(appliedThreshold).isEqualTo(127);
            assertThat(thresholded.rows()).isEqualTo(2);
            assertThat(thresholded.cols()).isEqualTo(3);
            assertThat(result)
                    .containsExactly(new byte[] {0, 0, 0, (byte) 255, (byte) 255, (byte) 255});
        } finally {
            source.release();
            thresholded.release();
        }
    }
}
