/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;
import javax.net.SocketFactory;
import oracle.security.pki.ldap.LdapSSLSocketFactory;
import org.junit.jupiter.api.Test;

public class LdapSSLSocketFactoryTest {
    @Test
    void createsDefaultSslSocketFactoryThroughConfiguredJcaFactories() {
        SocketFactory socketFactory = new LdapSSLSocketFactory(new Hashtable<>());

        assertThat(socketFactory).isInstanceOf(LdapSSLSocketFactory.class);
    }
}
