/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.pbcast.GMS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GMSTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void createsMembershipChangePolicyFromClassName() {
        GMS gms = new GMS();

        GMS returned = gms.setMembershipChangePolicy(GMS.DefaultMembershipPolicy.class.getName());

        assertThat(returned).isSameAs(gms);
        assertThat(gms.getMembershipChangePolicy()).isInstanceOf(GMS.DefaultMembershipPolicy.class);
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
