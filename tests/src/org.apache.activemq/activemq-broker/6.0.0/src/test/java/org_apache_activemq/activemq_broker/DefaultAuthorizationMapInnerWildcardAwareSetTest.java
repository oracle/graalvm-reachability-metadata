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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthorizationMapInnerWildcardAwareSetTest {

    @Test
    void wildcardAclMatchesEveryPrincipal() throws Exception {
        AuthorizationEntry entry = new AuthorizationEntry();
        entry.setQueue(">");
        entry.setRead("*");
        DefaultAuthorizationMap authorizationMap = new DefaultAuthorizationMap(List.of(entry));

        Set<Object> readAccess = authorizationMap.getReadACLs(new ActiveMQQueue("orders.eu"));

        assertThat(readAccess).contains(new GroupPrincipal("any-group"));
    }
}
