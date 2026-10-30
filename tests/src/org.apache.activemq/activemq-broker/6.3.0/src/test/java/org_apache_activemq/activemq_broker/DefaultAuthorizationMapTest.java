/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.jaas.GroupPrincipal;
import org.apache.activemq.security.AuthorizationEntry;
import org.apache.activemq.security.DefaultAuthorizationMap;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthorizationMapTest {

    @Test
    void resolvesConfiguredGroupsForMatchingQueues() throws Exception {
        AuthorizationEntry entry = new AuthorizationEntry();
        entry.setQueue("orders.>");
        entry.setRead("operators, auditors");
        entry.setWrite("operators");
        entry.setAdmin("administrators");
        DefaultAuthorizationMap map = new DefaultAuthorizationMap(List.of(entry));
        ActiveMQQueue orders = new ActiveMQQueue("orders.pending");
        assertThat(map.getReadACLs(orders)).containsExactlyInAnyOrder(
                new GroupPrincipal("operators"), new GroupPrincipal("auditors"));
        assertThat(map.getWriteACLs(orders)).containsExactly(new GroupPrincipal("operators"));
        assertThat(map.getAdminACLs(orders)).containsExactly(new GroupPrincipal("administrators"));
        assertThat(map.getReadACLs(new ActiveMQQueue("invoices.pending"))).isEmpty();
    }

    @Test
    void acceptsBeanStyleCustomGroupPrincipals() throws Exception {
        AuthorizationEntry entry = new AuthorizationEntry();
        entry.setQueue("reports.>");
        entry.setGroupClass(ConfigurableGroup.class.getName());
        entry.setRead("auditors");
        DefaultAuthorizationMap map = new DefaultAuthorizationMap(List.of(entry));
        assertThat(map.getReadACLs(new ActiveMQQueue("reports.daily")))
                .singleElement().isInstanceOfSatisfying(ConfigurableGroup.class,
                        group -> assertThat(group.getName()).isEqualTo("auditors"));
    }

    public static class ConfigurableGroup implements Principal {
        private String name;

        public ConfigurableGroup() {
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
