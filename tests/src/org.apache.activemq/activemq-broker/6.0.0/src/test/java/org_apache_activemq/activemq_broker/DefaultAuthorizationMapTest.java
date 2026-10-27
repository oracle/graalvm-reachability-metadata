/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.jaas.GroupPrincipal;
import org.apache.activemq.network.NetworkBridgeConfiguration;
import org.apache.activemq.security.DefaultAuthorizationMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthorizationMapTest {

    @Test
    void createsConfiguredGroupPrincipalTypes() throws Exception {
        Object principal = DefaultAuthorizationMap.createGroupPrincipal(
                "operators", DefaultAuthorizationMap.DEFAULT_GROUP_CLASS);
        Object beanStylePrincipal = DefaultAuthorizationMap.createGroupPrincipal(
                "bridge-operators", NetworkBridgeConfiguration.class.getName());

        assertThat(principal).isEqualTo(new GroupPrincipal("operators"));
        assertThat(beanStylePrincipal).isInstanceOf(NetworkBridgeConfiguration.class);
        NetworkBridgeConfiguration configuration = (NetworkBridgeConfiguration) beanStylePrincipal;
        assertThat(configuration.getName()).isEqualTo("bridge-operators");
    }
}
