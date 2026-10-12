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
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameRecorder;
import org.junit.jupiter.api.Test;

public class FrameRecorderTest {
    @Test
    void initializesResolvesAndSelectsTheFfmpegBackend() throws Exception {
        List<String> originalBackends = new ArrayList<>(FrameRecorder.list);
        try {
            FrameRecorder.list.clear();
            FrameRecorder.list.add("FFmpeg");

            FrameRecorder.init();

            Class<? extends FrameRecorder> directlyResolved = FrameRecorder.get("FFmpegFrameRecorder");
            Class<? extends FrameRecorder> conventionallyResolved = FrameRecorder.get("FFmpeg");
            assertThat(directlyResolved).isEqualTo(FFmpegFrameRecorder.class);
            assertThat(conventionallyResolved).isEqualTo(directlyResolved);

            Class<? extends FrameRecorder> selected = FrameRecorder.getDefault();
            assertThat(selected).isEqualTo(directlyResolved);
        } finally {
            FrameRecorder.list.clear();
            FrameRecorder.list.addAll(originalBackends);
        }
    }

    @Test
    void createsTheOpenCvBackendThroughTheNamedFactory() throws Exception {
        try (FrameRecorder recorder = FrameRecorder.create("OpenCV", "recording.avi", 320, 240)) {
            assertThat(recorder).isInstanceOf(OpenCVFrameRecorder.class);
            assertThat(recorder.getImageWidth()).isEqualTo(320);
            assertThat(recorder.getImageHeight()).isEqualTo(240);
            assertThat(recorder.getFrameRate()).isEqualTo(30.0);
        }
    }
}
