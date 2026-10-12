/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacv;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.javacv.FrameRecorder;
import org.junit.jupiter.api.Test;

public class FrameRecorderTest {
    @Test
    void resolvesTheOpenCvBackend() throws Exception {
        Class<? extends FrameRecorder> resolved = FrameRecorder.get("OpenCVFrameRecorder");

        assertThat(resolved.getName()).isEqualTo("org.bytedeco.javacv.OpenCVFrameRecorder");
    }
}
