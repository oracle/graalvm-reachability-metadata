/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.plugin.version.PluginVersionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginVersionManagerAnonymous1Test {
    @Test
    public void roleNameIsDerivedFromPluginVersionManagerInterfaceClass() {
        assertEquals("org.apache.maven.plugin.version.PluginVersionManager", PluginVersionManager.ROLE);
    }
}
