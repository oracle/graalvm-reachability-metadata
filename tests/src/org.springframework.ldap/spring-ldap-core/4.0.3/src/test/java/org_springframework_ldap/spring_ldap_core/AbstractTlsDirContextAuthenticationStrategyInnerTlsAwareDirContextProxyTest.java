/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextProxy;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.net.ssl.SSLContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractTlsDirContextAuthenticationStrategyInnerTlsAwareDirContextProxyTest {
    @Test
    void forwardsLdapCallsAndClosesTheNegotiatedTlsSession() throws Exception {
        SSLContext tls = TlsContexts.create();
        try (LdapServer server = new LdapServer(tls)) {
            DirContext context = server.tlsContextSource(tls).getReadWriteContext();
            DirContext target = ((DirContextProxy) context).getTargetContext();
            try {
                assertThat(context.getAttributes("cn=Grace").get("sn").get()).isEqualTo("Hopper");
                assertThatThrownBy(() -> context.getAttributes("cn=missing")).isInstanceOf(NameNotFoundException.class);
                assertThat(context.getEnvironment().get(Context.SECURITY_PRINCIPAL)).isEqualTo("cn=admin");
            } finally {
                context.close();
            }
            assertThatThrownBy(() -> target.getAttributes("cn=Ada")).isInstanceOf(NamingException.class);
        }
    }
}
