/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.transaction.compensating.manager.TransactionAwareContextSourceProxy;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import static org.assertj.core.api.Assertions.assertThat;

public class CompensatingTransactionUtilsTest {
    @Test
    void executesWritesImmediatelyWhenNoTransactionIsActive() throws Exception {
        try (LdapServer server = new LdapServer()) {
            TransactionAwareContextSourceProxy source = new TransactionAwareContextSourceProxy(server.contextSource());
            DirContext context = source.getReadWriteContext();
            try {
                context.modifyAttributes("cn=Ada", new ModificationItem[]{
                        new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                new BasicAttribute("description", "analytical engine"))});
                assertThat(context.getAttributes("cn=Ada").get("description").get()).isEqualTo("analytical engine");
                context.rename("cn=Grace", "cn=Rear Admiral Hopper");
                assertThat(context.getAttributes("cn=Rear Admiral Hopper").get("sn").get()).isEqualTo("Hopper");
            } finally {
                context.close();
            }
        }
    }
}
