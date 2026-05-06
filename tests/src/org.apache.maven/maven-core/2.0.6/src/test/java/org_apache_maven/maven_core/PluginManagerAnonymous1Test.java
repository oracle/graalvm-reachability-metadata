/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginManagerAnonymous1Test {
    @Test
    public void roleNameIsDerivedFromPluginManagerInterfaceClass() {
        assertEquals("org.apache.maven.plugin.PluginManager", PluginManager.ROLE);
    }
}
