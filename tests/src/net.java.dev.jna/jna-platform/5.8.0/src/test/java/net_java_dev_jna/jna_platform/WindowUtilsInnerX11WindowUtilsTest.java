/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BufferCapabilities;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.ImageCapabilities;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class WindowUtilsInnerX11WindowUtilsTest {
    @Test
    void getVisualIdReadsVisualFromGraphicsConfiguration() throws Exception {
        TestX11GraphicsConfiguration configuration = new TestX11GraphicsConfiguration(0x2AL);

        Method getVisualId = x11WindowUtilsClass().getDeclaredMethod("getVisualID", GraphicsConfiguration.class);
        getVisualId.setAccessible(true);

        assertThat(getVisualId.invoke(null, configuration)).isEqualTo(0x2AL);
        assertThat(configuration.getVisualCallCount()).isEqualTo(1);
    }

    private static Class<?> x11WindowUtilsClass() throws ClassNotFoundException {
        return Class.forName("com.sun.jna.platform.WindowUtils$X11WindowUtils");
    }

    public static final class TestX11GraphicsConfiguration extends GraphicsConfiguration {
        private final GraphicsDevice device = new TestGraphicsDevice(this);
        private final long visual;
        private int visualCallCount;

        private TestX11GraphicsConfiguration(long visual) {
            this.visual = visual;
        }

        public long getVisual() {
            visualCallCount++;
            return visual;
        }

        private int getVisualCallCount() {
            return visualCallCount;
        }

        @Override
        public GraphicsDevice getDevice() {
            return device;
        }

        @Override
        public ColorModel getColorModel() {
            return ColorModel.getRGBdefault();
        }

        @Override
        public ColorModel getColorModel(int transparency) {
            return ColorModel.getRGBdefault();
        }

        @Override
        public AffineTransform getDefaultTransform() {
            return new AffineTransform();
        }

        @Override
        public AffineTransform getNormalizingTransform() {
            return new AffineTransform();
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(0, 0, 1, 1);
        }

        @Override
        public BufferCapabilities getBufferCapabilities() {
            return new BufferCapabilities(
                new ImageCapabilities(false),
                new ImageCapabilities(false),
                BufferCapabilities.FlipContents.UNDEFINED
            );
        }

        @Override
        public ImageCapabilities getImageCapabilities() {
            return new ImageCapabilities(false);
        }
    }

    private static final class TestGraphicsDevice extends GraphicsDevice {
        private final GraphicsConfiguration configuration;

        private TestGraphicsDevice(GraphicsConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public int getType() {
            return TYPE_RASTER_SCREEN;
        }

        @Override
        public String getIDstring() {
            return "test-screen";
        }

        @Override
        public GraphicsConfiguration[] getConfigurations() {
            return new GraphicsConfiguration[] { configuration };
        }

        @Override
        public GraphicsConfiguration getDefaultConfiguration() {
            return configuration;
        }

        @Override
        public boolean isFullScreenSupported() {
            return false;
        }

        @Override
        public boolean isDisplayChangeSupported() {
            return false;
        }
    }
}
