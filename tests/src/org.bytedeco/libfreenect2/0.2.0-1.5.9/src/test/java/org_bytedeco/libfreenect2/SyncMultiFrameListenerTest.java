/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libfreenect2;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.libfreenect2.Frame;
import org.bytedeco.libfreenect2.FrameMap;
import org.bytedeco.libfreenect2.SyncMultiFrameListener;
import org.junit.jupiter.api.Test;

public class SyncMultiFrameListenerTest {
    private static final int FRAME_WAIT_TIMEOUT_MILLISECONDS = 10_000;

    @Test
    void synchronizesSubscribedFrameTypes() {
        try (SyncMultiFrameListener listener = new SyncMultiFrameListener(Frame.Color | Frame.Depth);
                FrameMap receivedFrames = new FrameMap()) {
            assertThat(listener.hasNewFrame()).isFalse();

            long colorAddress = submitFrame(listener, Frame.Color, Frame.RGBX, 101);
            assertThat(listener.hasNewFrame()).isFalse();

            long depthAddress = submitFrame(listener, Frame.Depth, Frame.Float, 102);
            assertThat(listener.hasNewFrame()).isTrue();

            assertThat(listener.waitForNewFrame(receivedFrames, FRAME_WAIT_TIMEOUT_MILLISECONDS)).isTrue();
            try {
                assertThat(listener.hasNewFrame()).isFalse();
                assertThat(receivedFrames.get(Frame.Color).address()).isEqualTo(colorAddress);
                assertThat(receivedFrames.get(Frame.Depth).address()).isEqualTo(depthAddress);
                assertThat(receivedFrames.get(Frame.Color).timestamp()).isEqualTo(101);
                assertThat(receivedFrames.get(Frame.Depth).timestamp()).isEqualTo(102);
            } finally {
                listener.release(receivedFrames);
            }

            assertThat(receivedFrames.empty()).isTrue();
        }
    }

    private static long submitFrame(SyncMultiFrameListener listener, int type, int format, int timestamp) {
        Frame frame = new Frame(2, 1, 4);
        frame.format(format).timestamp(timestamp);
        long address = frame.address();
        boolean accepted;
        try {
            accepted = listener.onNewFrame(type, frame);
        } catch (RuntimeException | Error failure) {
            frame.close();
            throw failure;
        }

        if (accepted) {
            // Accepted frames are owned and deleted by SyncMultiFrameListener.release().
            frame.deallocate(false);
        } else {
            frame.close();
        }
        assertThat(accepted).isTrue();
        return address;
    }
}
