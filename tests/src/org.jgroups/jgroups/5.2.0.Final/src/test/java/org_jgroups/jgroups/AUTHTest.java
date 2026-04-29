/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.auth.FixedMembershipToken;
import org.jgroups.protocols.AUTH;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AUTHTest {
    @Test
    void createsConfiguredAuthTokenFromClassName() throws Exception {
        AUTH auth = new AUTH();

        auth.setAuthClass(FixedMembershipToken.class.getName());

        assertThat(auth.getAuthClass()).isEqualTo(FixedMembershipToken.class.getName());
        assertThat(auth.getAuthToken()).isInstanceOf(FixedMembershipToken.class);
    }
}
