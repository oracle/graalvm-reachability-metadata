/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextProxy;

import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapContext;
import javax.net.ssl.SSLContext;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTlsDirContextAuthenticationStrategyTest {
    @Test
    void negotiatesStartTlsAndReturnsAGracefullyClosingLdapContext() throws Exception {
        SSLContext tls = TlsContexts.create();
        try (LdapServer server = new LdapServer(tls)) {
            DirContext context = server.tlsContextSource(tls).getReadWriteContext();
            try {
                assertThat(context).isInstanceOf(LdapContext.class).isInstanceOf(DirContextProxy.class);
                DirContext target = ((DirContextProxy) context).getTargetContext();
                assertThat(target).isNotSameAs(context);
                assertThat(context.getAttributes("cn=Ada").get("sn").get()).isEqualTo("Lovelace");
            } finally {
                context.close();
            }
        }
    }
}
