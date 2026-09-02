/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;

import org.apache.activemq.security.DefaultAuthorizationMap;
import org.junit.jupiter.api.Test;

public class DefaultAuthorizationMapTest {

    @Test
    void createsConfiguredGroupPrincipalImplementations() throws Exception {
        Object defaultPrincipal = DefaultAuthorizationMap.createGroupPrincipal(
                "operators", DefaultAuthorizationMap.DEFAULT_GROUP_CLASS);
        Object setterPrincipal = DefaultAuthorizationMap.createGroupPrincipal(
                "auditors", SetterPrincipal.class.getName());

        assertThat(defaultPrincipal).isInstanceOf(Principal.class);
        assertThat(((Principal) defaultPrincipal).getName()).isEqualTo("operators");
        assertThat(setterPrincipal).isInstanceOf(SetterPrincipal.class);
        assertThat(((SetterPrincipal) setterPrincipal).getName()).isEqualTo("auditors");
    }

    public static final class SetterPrincipal implements Principal {
        private String name;

        public SetterPrincipal() { }

        @Override
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
