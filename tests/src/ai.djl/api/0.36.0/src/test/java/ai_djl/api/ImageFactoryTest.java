/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import org.junit.jupiter.api.Test;

public class ImageFactoryTest {
    @Test
    void createsImageThroughDiscoveredFactory() {
        Image image = ImageFactory.getInstance().fromPixels(
                new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff}, 2, 2);

        assertThat(image.getWidth()).isEqualTo(2);
        assertThat(image.getHeight()).isEqualTo(2);
    }
}
