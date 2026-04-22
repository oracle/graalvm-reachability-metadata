/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import org.junit.jupiter.api.Test;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class WindowUtilsX11WindowUtils1Test {

    @Test
    void getVisualIdReadsVisualFromGraphicsConfiguration() throws Exception {
        Class<?> x11WindowUtilsClass = Class.forName("com.sun.jna.platform.WindowUtils$X11WindowUtils");
        Method getVisualIdMethod = x11WindowUtilsClass.getDeclaredMethod("getVisualID", GraphicsConfiguration.class);
        getVisualIdMethod.setAccessible(true);

        long visualId = ((Long) getVisualIdMethod.invoke(null, new VisualGraphicsConfiguration(0x5A17L))).longValue();

        assertThat(visualId).isEqualTo(0x5A17L);
    }

    public static final class VisualGraphicsConfiguration extends GraphicsConfiguration {
        private final long visual;
        private final GraphicsDevice device;

        public VisualGraphicsConfiguration(long visual) {
            this.visual = visual;
            this.device = new VisualGraphicsDevice(this);
        }

        public long getVisual() {
            return visual;
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
            return getColorModel();
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
    }

    private static final class VisualGraphicsDevice extends GraphicsDevice {
        private final GraphicsConfiguration configuration;

        private VisualGraphicsDevice(GraphicsConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public int getType() {
            return TYPE_RASTER_SCREEN;
        }

        @Override
        public String getIDstring() {
            return "visual-device";
        }

        @Override
        public GraphicsConfiguration[] getConfigurations() {
            return new GraphicsConfiguration[]{configuration};
        }

        @Override
        public GraphicsConfiguration getDefaultConfiguration() {
            return configuration;
        }
    }
}
