/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.transaction.compensating.manager.ContextSourceTransactionManager;
import org.springframework.ldap.transaction.compensating.manager.TransactionAwareContextSourceProxy;
import org.springframework.ldap.transaction.compensating.support.DefaultTempEntryRenamingStrategy;
import org.springframework.transaction.support.TransactionTemplate;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransactionAwareDirContextInvocationHandlerTest {
    @Test
    void keepsTheConnectionOpenWithinATransactionAndForwardsReads() throws Exception {
        try (LdapServer server = new LdapServer()) {
            LdapContextSource target = server.contextSource();
            ContextSourceTransactionManager manager = new ContextSourceTransactionManager();
            manager.setContextSource(target);
            manager.setRenamingStrategy(new DefaultTempEntryRenamingStrategy());
            manager.afterPropertiesSet();
            TransactionAwareContextSourceProxy source = new TransactionAwareContextSourceProxy(target);
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                DirContext context = source.getReadWriteContext();
                try {
                    assertThat(context.getAttributes("cn=Ada").get("sn").get()).isEqualTo("Lovelace");
                    context.close();
                    assertThat(context.getAttributes("cn=Grace").get("sn").get()).isEqualTo("Hopper");
                    assertThatThrownBy(() -> context.getAttributes("cn=missing")).isInstanceOf(NameNotFoundException.class);
                } catch (NamingException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        }
    }
}
