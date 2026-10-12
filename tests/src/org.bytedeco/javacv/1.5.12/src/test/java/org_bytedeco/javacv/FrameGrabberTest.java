/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.junit.jupiter.api.Test;

public class FrameGrabberTest {
    @Test
    void initializesSelectsAndCreatesTheOpenCvBackend() throws Exception {
        List<String> originalBackends = new ArrayList<>(FrameGrabber.list);
        try {
            FrameGrabber.list.clear();
            FrameGrabber.list.add("OpenCV");

            FrameGrabber.init();
            assertThat(FrameGrabber.getDefault()).isEqualTo(OpenCVFrameGrabber.class);

            try (FrameGrabber grabber = FrameGrabber.create("OpenCV", "input.avi")) {
                grabber.setImageWidth(160);
                grabber.setImageHeight(120);
                assertThat(grabber).isInstanceOf(OpenCVFrameGrabber.class);
                assertThat(grabber.getImageWidth()).isEqualTo(160);
                assertThat(grabber.getImageHeight()).isEqualTo(120);
            }
        } finally {
            FrameGrabber.list.clear();
            FrameGrabber.list.addAll(originalBackends);
        }
    }
}
