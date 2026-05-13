/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interactivity_api;

import org.codehaus.plexus.components.interactivity.InputHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InputHandlerAnonymous1Test {
    @Test
    void exposesInputHandlerRoleName() {
        assertThat(InputHandler.ROLE).isEqualTo(InputHandler.class.getName());
    }
}
