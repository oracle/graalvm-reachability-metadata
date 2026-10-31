/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComponentConfiguratorAnonymous1Test {
    @Test
    public void exposesInterfaceRoleName() {
        assertEquals(ComponentConfigurator.class.getName(), ComponentConfigurator.ROLE);
    }
}
