/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.WindowUtils;

public class WindowUtilsInnerMacWindowUtilsAnonymous1Test {
    private static final float EXPECTED_ALPHA = 0.375f;

    @Test
    void setWindowAlphaDefersNativeAccessUntilWindowIsDisplayable() {
        String originalHeadless = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "false");

        PeerAwareWindow window = null;
        try {
            window = new PeerAwareWindow();

            WindowUtils.setWindowAlpha(window, EXPECTED_ALPHA);

            assertThat(window.peer().alpha()).isEqualTo(1.0f);
            assertThat(window.getHierarchyListeners()).isNotEmpty();
        }
        finally {
            restoreProperty("java.awt.headless", originalHeadless);
            if (window != null) {
                window.dispose();
            }
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

    public static final class PeerAwareWindow extends Window {
        private static final long serialVersionUID = 1L;

        private final PeerAlphaController peer = new PeerAlphaController();

        PeerAwareWindow() {
            super(null, TestGraphicsConfiguration.INSTANCE);
        }

        public PeerAlphaController getPeer() {
            return peer;
        }

        PeerAlphaController peer() {
            return peer;
        }

        @Override
        public boolean isDisplayable() {
            return false;
        }
    }

    public static final class PeerAlphaController {
        private float alpha = 1.0f;

        public void setAlpha(float newAlpha) {
            this.alpha = newAlpha;
        }

        float alpha() {
            return alpha;
        }
    }

    private static final class TestGraphicsConfiguration extends GraphicsConfiguration {
        private static final TestGraphicsConfiguration INSTANCE = new TestGraphicsConfiguration();
        private static final ColorModel COLOR_MODEL = ColorModel.getRGBdefault();
        private static final Rectangle BOUNDS = new Rectangle(0, 0, 128, 128);
        private static final AffineTransform IDENTITY_TRANSFORM = new AffineTransform();

        private final GraphicsDevice device = new TestGraphicsDevice(this);

        @Override
        public GraphicsDevice getDevice() {
            return device;
        }

        @Override
        public ColorModel getColorModel() {
            return COLOR_MODEL;
        }

        @Override
        public ColorModel getColorModel(int transparency) {
            if (transparency == Transparency.TRANSLUCENT) {
                return ColorModel.getRGBdefault();
            }
            return COLOR_MODEL;
        }

        @Override
        public AffineTransform getDefaultTransform() {
            return new AffineTransform(IDENTITY_TRANSFORM);
        }

        @Override
        public AffineTransform getNormalizingTransform() {
            return new AffineTransform(IDENTITY_TRANSFORM);
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(BOUNDS);
        }

        @Override
        public BufferedImage createCompatibleImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private static final class TestGraphicsDevice extends GraphicsDevice {
        private final GraphicsConfiguration configuration;

        TestGraphicsDevice(GraphicsConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public int getType() {
            return TYPE_RASTER_SCREEN;
        }

        @Override
        public String getIDstring() {
            return "test-raster-screen";
        }

        @Override
        public GraphicsConfiguration[] getConfigurations() {
            return new GraphicsConfiguration[]{ configuration };
        }

        @Override
        public GraphicsConfiguration getDefaultConfiguration() {
            return configuration;
        }
    }
}
