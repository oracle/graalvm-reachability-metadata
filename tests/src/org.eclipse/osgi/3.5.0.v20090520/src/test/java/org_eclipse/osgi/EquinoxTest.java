/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.util.HashMap;

import org.eclipse.osgi.launch.EquinoxFactory;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EquinoxTest {
    @Test
    public void exposesSystemBundleIdentityBeforeInitialization() throws Exception {
        Framework framework = new EquinoxFactory().newFramework(new HashMap<String, String>());
        assertEquals(Bundle.INSTALLED, framework.getState());
        assertEquals(0L, framework.getBundleId());
        assertEquals(Constants.SYSTEM_BUNDLE_LOCATION, framework.getLocation());
        assertEquals("org.eclipse.osgi", framework.getSymbolicName());
        assertNull(framework.getBundleContext());
        assertEquals(FrameworkEvent.STOPPED, framework.waitForStop(10000L).getType());
    }
}
