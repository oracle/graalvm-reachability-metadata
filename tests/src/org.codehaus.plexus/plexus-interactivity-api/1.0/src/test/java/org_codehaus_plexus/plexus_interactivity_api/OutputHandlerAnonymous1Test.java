/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interactivity_api;

import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OutputHandlerAnonymous1Test {
    @Test
    void exposesOutputHandlerRoleName() {
        assertThat(OutputHandler.ROLE).isEqualTo(OutputHandler.class.getName());
    }
}
