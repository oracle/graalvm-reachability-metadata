/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.pool2.factory.PoolConfig;
import org.springframework.ldap.pool2.factory.PooledContextSource;

import javax.naming.NameNotFoundException;
import javax.naming.directory.DirContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DirContextPooledObjectFactoryInnerFailureAwareContextProxyTest {
    @Test
    void reusesConnectionsAfterForwardingReadsAndRecoverableNamingExceptions() throws Exception {
        try (LdapServer server = new LdapServer()) {
            PoolConfig config = new PoolConfig();
            config.setJmxEnabled(false);
            config.setMaxWaitMillis(10000);
            PooledContextSource pool = new PooledContextSource(config);
            pool.setContextSource(server.contextSource());
            try {
                DirContext context = pool.getReadWriteContext();
                try {
                    assertThat(context.getAttributes("cn=Grace").get("sn").get()).isEqualTo("Hopper");
                    assertThatThrownBy(() -> context.getAttributes("cn=missing")).isInstanceOf(NameNotFoundException.class);
                } finally {
                    context.close();
                }
                assertThat(pool.getNumIdleWrite()).isEqualTo(1);
                DirContext reused = pool.getReadWriteContext();
                try {
                    assertThat(reused.getAttributes("cn=Ada").get("sn").get()).isEqualTo("Lovelace");
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
