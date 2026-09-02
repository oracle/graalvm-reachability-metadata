/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.activemq.security.DefaultAuthorizationMap;
import org.apache.activemq.security.TempDestinationAuthorizationEntry;
import org.junit.jupiter.api.Test;

public class DefaultAuthorizationMapInnerWildcardAwareSetTest {

    @Test
    void wildcardTemporaryDestinationAclMatchesEveryPrincipal() throws Exception {
        TempDestinationAuthorizationEntry entry = new TempDestinationAuthorizationEntry();
        entry.setAdmin("*");
        DefaultAuthorizationMap authorizationMap = new DefaultAuthorizationMap();
        authorizationMap.setTempDestinationAuthorizationEntry(entry);

        Set<Object> adminAcls = authorizationMap.getTempDestinationAdminACLs();

        assertThat(adminAcls).hasSize(1);
        assertThat(adminAcls.contains("any authenticated principal")).isTrue();
    }
}
