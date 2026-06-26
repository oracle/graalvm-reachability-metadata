/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GraphicsConfiguration;

import org.junit.jupiter.api.Test;

import com.sun.jna.Platform;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.platform.unix.X11.Display;

public class WindowUtilsInnerX11WindowUtilsTest {
    @Test
    void alphaCompatibleGraphicsConfigurationInspectsX11Visuals() {
        String originalHeadless = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "false");
        Display display = null;
        try {
            assertThat(Platform.isX11()).isTrue();
            display = X11.INSTANCE.XOpenDisplay(null);
            assertThat(display).isNotNull();
            assertThat(WindowUtils.isWindowAlphaSupported()).isTrue();

            GraphicsConfiguration configuration = WindowUtils.getAlphaCompatibleGraphicsConfiguration();

            assertThat(configuration).isNotNull();
        }
        finally {
            if (display != null) {
                X11.INSTANCE.XCloseDisplay(display);
            }
            restoreProperty("java.awt.headless", originalHeadless);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        }
        else {
            System.setProperty(name, value);
        }
    }
}
