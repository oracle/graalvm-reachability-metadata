/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.awt.Image;
import java.util.concurrent.TimeUnit;

import junit.awtui.Logo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class AwtLogoTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void loadsPackagedLogoImage() {
        Logo logo = new Logo();
        Image image = logo.loadImage("logo.gif");

        assertThat(image).isNotNull();
        assertThat(logo.getWidth()).isPositive();
        assertThat(logo.getHeight()).isPositive();
    }
}
