/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.composition.ComponentComposerManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentComposerManagerAnonymous1Test {
    @Test
    public void exposesInterfaceRoleName() {
        assertThat(ComponentComposerManager.ROLE)
            .isEqualTo("org.codehaus.plexus.component.composition.ComponentComposerManager");
    }
}
