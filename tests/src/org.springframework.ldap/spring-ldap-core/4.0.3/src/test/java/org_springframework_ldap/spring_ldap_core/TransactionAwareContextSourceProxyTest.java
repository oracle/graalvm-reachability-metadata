/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DirContextProxy;
import org.springframework.ldap.transaction.compensating.manager.TransactionAwareContextSourceProxy;

import javax.naming.directory.DirContext;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionAwareContextSourceProxyTest {
    @Test
    void exposesATransactionAwareViewOfARealConnection() throws Exception {
        try (LdapServer server = new LdapServer()) {
            ContextSource target = server.contextSource();
            TransactionAwareContextSourceProxy source = new TransactionAwareContextSourceProxy(target);
            assertThat(source.getTarget()).isSameAs(target);
            DirContext context = source.getReadOnlyContext();
            try {
                assertThat(context).isInstanceOf(DirContextProxy.class);
                assertThat(((DirContextProxy) context).getTargetContext()).isNotSameAs(context);
                assertThat(context.getAttributes("cn=Ada").get("cn").get()).isEqualTo("Ada");
            } finally {
                context.close();
            }
        }
    }
}
