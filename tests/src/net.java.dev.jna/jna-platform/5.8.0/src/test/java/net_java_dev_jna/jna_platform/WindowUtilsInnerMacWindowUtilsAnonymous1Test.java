/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.WindowUtils.NativeWindowUtils;
import java.awt.BufferCapabilities;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.ImageCapabilities;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class WindowUtilsInnerMacWindowUtilsAnonymous1Test {
    @Test
    void setWindowAlphaUpdatesDisplayableWindowPeer() throws Exception {
        DisplayableWindow window = new DisplayableWindow();

        macWindowUtils().setWindowAlpha(window, 0.42f);

        assertThat(window.getPeer().getAlpha()).isEqualTo(0.42f);
    }

    private static NativeWindowUtils macWindowUtils() throws Exception {
        Class<?> implementationClass = Class.forName("com.sun.jna.platform.WindowUtils$MacWindowUtils");
        Constructor<?> constructor = implementationClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (NativeWindowUtils) constructor.newInstance();
    }

    public static final class DisplayableWindow extends Window {
        private static final long serialVersionUID = 1L;

        private final AlphaPeer alphaPeer = new AlphaPeer();

        public DisplayableWindow() {
            super((Window) null, new TestGraphicsConfiguration());
        }

        @Override
        public boolean isDisplayable() {
            return true;
        }

        public AlphaPeer getPeer() {
            return alphaPeer;
        }
    }

    public static final class AlphaPeer {
        private float alpha = Float.NaN;

        public void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        public float getAlpha() {
            return alpha;
        }
    }

    private static final class TestGraphicsConfiguration extends GraphicsConfiguration {
        private final GraphicsDevice device = new TestGraphicsDevice(this);

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
