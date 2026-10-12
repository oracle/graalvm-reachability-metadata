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
import org.junit.jupiter.api.Test;

public class FrameRecorderTest {
    @Test
    void initializesSelectsAndCreatesTheFfmpegBackend() throws Exception {
        List<String> originalBackends = new ArrayList<>(FrameRecorder.list);
        try {
            FrameRecorder.list.clear();
            FrameRecorder.list.add("FFmpeg");

            FrameRecorder.init();
            assertThat(FrameRecorder.getDefault()).isEqualTo(FFmpegFrameRecorder.class);

            try (FrameRecorder recorder = FrameRecorder.create("FFmpeg", "output.mp4", 160, 120)) {
                assertThat(recorder).isInstanceOf(FFmpegFrameRecorder.class);
                assertThat(recorder.getImageWidth()).isEqualTo(160);
                assertThat(recorder.getImageHeight()).isEqualTo(120);
            }
        } finally {
            FrameRecorder.list.clear();
            FrameRecorder.list.addAll(originalBackends);
        }
    }
}
