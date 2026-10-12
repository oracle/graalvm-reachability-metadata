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
    void initializesResolvesAndSelectsTheOpenCvBackend() throws Exception {
        List<String> originalBackends = new ArrayList<>(FrameGrabber.list);
        try {
            FrameGrabber.list.clear();
            FrameGrabber.list.add("OpenCV");

            FrameGrabber.init();

            Class<? extends FrameGrabber> resolved = FrameGrabber.get("OpenCV");
            assertThat(resolved).isEqualTo(OpenCVFrameGrabber.class);

            Class<? extends FrameGrabber> selected = FrameGrabber.getDefault();
            assertThat(selected).isEqualTo(resolved);
        } finally {
            FrameGrabber.list.clear();
            FrameGrabber.list.addAll(originalBackends);
        }
    }

    @Test
    void createsTheOpenCvBackendThroughTheNamedFactory() throws Exception {
        try (FrameGrabber grabber = FrameGrabber.create("OpenCV", 3)) {
            assertThat(grabber).isInstanceOf(OpenCVFrameGrabber.class);
            assertThat(grabber.getGamma()).isEqualTo(2.2);
        }
    }
}
