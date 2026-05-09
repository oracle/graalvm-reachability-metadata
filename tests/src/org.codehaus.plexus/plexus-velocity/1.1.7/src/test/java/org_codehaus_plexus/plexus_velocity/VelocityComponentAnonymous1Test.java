/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.velocity.VelocityComponent;
import org.junit.jupiter.api.Test;

public class VelocityComponentAnonymous1Test {
    @Test
    void roleInitializesVelocityComponentClassLiteral() {
        assertThat(VelocityComponent.ROLE).isEqualTo(VelocityComponent.class.getName());
    }
}
