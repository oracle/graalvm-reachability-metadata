/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.support.SingleContextSource;

import javax.naming.NameNotFoundException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SingleContextSourceInnerNonClosingDirContextInvocationHandlerTest {
    @Test
    void forwardsOperationsAndPreservesNamingExceptionsWithoutClosingTheConnection() throws Exception {
        try (LdapServer server = new LdapServer()) {
            SingleContextSource source = new SingleContextSource(server.contextSource().getReadWriteContext());
            try {
                DirContext context = source.getReadWriteContext();
                context.modifyAttributes("cn=Ada", new ModificationItem[]{
                        new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("description", "shared"))});
                context.close();
                assertThat(context.getAttributes("cn=Ada").get("description").get()).isEqualTo("shared");
                assertThatThrownBy(() -> context.getAttributes("cn=missing")).isInstanceOf(NameNotFoundException.class);
                assertThat(context.getAttributes("cn=Grace").get("sn").get()).isEqualTo("Hopper");
            } finally {
                source.destroy();
            }
        }
    }
}
