/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libfreenect2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bytedeco.libfreenect2.Frame;
import org.bytedeco.libfreenect2.FrameMap;
import org.junit.jupiter.api.Test;

public class FrameMapTest {
    @Test
    void storesAndIteratesFramesByStreamType() {
        try (Frame colorFrame = new Frame(2, 1, 4);
                Frame depthFrame = new Frame(1, 2, 4);
                FrameMap frames = new FrameMap()) {
            assertThat(frames.empty()).isTrue();

            frames.put(Frame.Color, colorFrame).put(Frame.Depth, depthFrame);

            assertThat(frames.size()).isEqualTo(2);
            assertThat(frames.get(Frame.Color).address()).isEqualTo(colorFrame.address());
            assertThat(frames.get(Frame.Depth).address()).isEqualTo(depthFrame.address());

            Map<Integer, Long> frameAddressesByStreamType = new LinkedHashMap<>();
            try (FrameMap.Iterator iterator = frames.begin(); FrameMap.Iterator end = frames.end()) {
                while (!iterator.equals(end)) {
                    frameAddressesByStreamType.put(iterator.first(), iterator.second().address());
                    iterator.increment();
                }
            }

            assertThat(frameAddressesByStreamType)
                    .containsExactly(
                            Map.entry(Frame.Color, colorFrame.address()),
                            Map.entry(Frame.Depth, depthFrame.address()));
        }
    }
}
