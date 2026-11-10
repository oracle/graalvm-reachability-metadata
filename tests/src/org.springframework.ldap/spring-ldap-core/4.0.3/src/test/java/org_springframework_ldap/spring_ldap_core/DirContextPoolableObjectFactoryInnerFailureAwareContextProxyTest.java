/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.pool.factory.PoolingContextSource;

import javax.naming.NameNotFoundException;
import javax.naming.directory.DirContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DirContextPoolableObjectFactoryInnerFailureAwareContextProxyTest {
    @Test
    void forwardsReadsAndReturnsUsableConnectionsAfterAnEntryIsNotFound() throws Exception {
        try (LdapServer server = new LdapServer()) {
            PoolingContextSource pool = new PoolingContextSource();
            pool.setContextSource(server.contextSource());
            pool.setMaxWait(10000);
            try {
                DirContext context = pool.getReadOnlyContext();
                try {
                    assertThat(context.getAttributes("cn=Ada").get("sn").get()).isEqualTo("Lovelace");
                    assertThatThrownBy(() -> context.getAttributes("cn=missing")).isInstanceOf(NameNotFoundException.class);
                } finally {
                    context.close();
                }
                assertThat(pool.getNumIdle()).isEqualTo(1);
                DirContext reused = pool.getReadOnlyContext();
                try {
                    assertThat(reused.getAttributes("cn=Grace").get("sn").get()).isEqualTo("Hopper");
                } finally {
                    reused.close();
                }
                assertThat(pool.getNumActive()).isZero();
            } finally {
                pool.destroy();
            }
        }
    }
}
