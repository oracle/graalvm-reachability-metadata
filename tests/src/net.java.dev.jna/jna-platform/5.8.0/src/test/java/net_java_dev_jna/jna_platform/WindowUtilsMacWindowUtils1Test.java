/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Window;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class WindowUtilsMacWindowUtils1Test {

    @Test
    void macWindowAlphaRunnableUsesWindowPeerAlphaSetter() throws Exception {
        Object macWindowUtils = instantiateMacWindowUtils();
        AlphaTrackingPeer peer = new AlphaTrackingPeer();
        HeadlessAlphaWindow window = allocateWindow(peer);

        setWindowAlpha(macWindowUtils, window, 0.35f);

        assertThat(peer.invocationCount()).isEqualTo(1);
        assertThat(peer.alpha()).isEqualTo(0.35f);
    }

    private static Object instantiateMacWindowUtils() throws Exception {
        Class<?> macWindowUtilsClass = Class.forName("com.sun.jna.platform.WindowUtils$MacWindowUtils");
        Constructor<?> constructor = macWindowUtilsClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static HeadlessAlphaWindow allocateWindow(AlphaTrackingPeer peer) throws Exception {
        HeadlessAlphaWindow window = (HeadlessAlphaWindow) unsafe().allocateInstance(HeadlessAlphaWindow.class);
        window.installPeer(peer);
        return window;
    }

    private static void setWindowAlpha(Object macWindowUtils, Window window, float alpha) throws Exception {
        Method method = macWindowUtils.getClass().getDeclaredMethod("setWindowAlpha", Window.class, float.class);
        method.setAccessible(true);
        method.invoke(macWindowUtils, window, alpha);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    public static final class HeadlessAlphaWindow extends Window {
        private AlphaTrackingPeer peer;

        private HeadlessAlphaWindow() {
            super((Window) null);
        }

        public Object getPeer() {
            return peer;
        }

        @Override
        public boolean isDisplayable() {
            return true;
        }

        private void installPeer(AlphaTrackingPeer peer) {
            this.peer = peer;
        }
    }

    public static final class AlphaTrackingPeer {
        private float alpha;
        private int invocationCount;

        public void setAlpha(float alpha) {
            this.alpha = alpha;
            this.invocationCount++;
        }

        private float alpha() {
            return alpha;
        }

        private int invocationCount() {
            return invocationCount;
        }
    }
}
