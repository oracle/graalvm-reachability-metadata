/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.internal.profile.DefaultProfileLogger;
import org.eclipse.osgi.internal.profile.Profile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProfileTest {
    @Test
    public void instantiatesTheConfiguredLoggerAndRecordsEvents() {
        try (FrameworkProperty implementation = new FrameworkProperty("osgi.profile.impl", DefaultProfileLogger.class.getName())) {
            Profile.initProps();
            Profile.logEnter("catalog-load", "load catalog");
            Profile.logTime("catalog-load", "catalog ready", "3 entries");
            Profile.logExit("catalog-load", "load catalog");
            String report = Profile.getProfileLog();
            assertTrue(report.contains("catalog-load"));
            assertTrue(report.contains("catalog ready"));
            assertTrue(report.contains("3 entries"));
        }
    }
}
