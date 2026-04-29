/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.stack.LargestWinningPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GMSTest {
    @Test
    void createsMembershipChangePolicyFromConfiguredClassName() {
        GMS gms = new GMS();

        GMS configured = gms.setMembershipChangePolicy(LargestWinningPolicy.class.getName());

        assertThat(configured).isSameAs(gms);
        assertThat(gms.getMembershipChangePolicy()).isInstanceOf(LargestWinningPolicy.class);
    }
}
