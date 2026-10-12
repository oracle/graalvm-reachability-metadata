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
import org.junit.jupiter.api.Test;

public class FrameGrabberTest {
    @Test
    void resolvesAndSelectsTheOpenCvBackend() throws Exception {
        List<String> originalBackends = new ArrayList<>(FrameGrabber.list);
        try {
            FrameGrabber.list.clear();
            FrameGrabber.list.add("OpenCVFrameGrabber");

            Class<? extends FrameGrabber> resolved = FrameGrabber.get("OpenCVFrameGrabber");
            assertThat(resolved.getName()).isEqualTo("org.bytedeco.javacv.OpenCVFrameGrabber");

            Class<? extends FrameGrabber> selected = FrameGrabber.getDefault();
            assertThat(selected).isNotNull();
            assertThat(selected.getName()).isEqualTo(resolved.getName());
        } finally {
            FrameGrabber.list.clear();
            FrameGrabber.list.addAll(originalBackends);
        }
    }
}
