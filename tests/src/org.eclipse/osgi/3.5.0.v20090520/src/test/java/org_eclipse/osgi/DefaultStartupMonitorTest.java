/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.core.runtime.internal.adaptor.DefaultStartupMonitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultStartupMonitorTest {
    public static class Splash implements Runnable {
        private int updates;
        private boolean closed;

        public void updateSplash() {
            updates++;
        }

        @Override
        public void run() {
            closed = true;
        }
    }

    @Test
    public void updatesAndClosesTheSplashHandler() {
        Splash splash = new Splash();
        DefaultStartupMonitor monitor = new DefaultStartupMonitor(splash);
        monitor.update();
        monitor.update();
        assertEquals(2, splash.updates);
        monitor.applicationRunning();
        assertTrue(splash.closed);
    }
}
