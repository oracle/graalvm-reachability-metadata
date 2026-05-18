/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interactivity_api;

import org.codehaus.plexus.components.interactivity.Prompter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrompterAnonymous1Test {
    @Test
    void exposesPrompterRoleName() {
        assertThat(Prompter.ROLE).isEqualTo(Prompter.class.getName());
    }
}
