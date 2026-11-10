/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.springframework.ldap.core.support.DefaultTlsDirContextAuthenticationStrategy;
import org.springframework.ldap.core.support.LdapContextSource;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.util.Map;

final class LdapServer implements AutoCloseable {
    private final InMemoryDirectoryServer server;

    LdapServer() throws Exception {
        this(null);
    }

    LdapServer(SSLContext tls) throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=org");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap",
                InetAddress.getLoopbackAddress(), 0, tls == null ? null : tls.getSocketFactory()));
        config.addAdditionalBindCredentials("cn=admin", "secret");
        server = new InMemoryDirectoryServer(config);
        try {
            server.add("dn: dc=example,dc=org", "objectClass: domain", "dc: example");
            server.add("dn: cn=Ada,dc=example,dc=org", "objectClass: person", "cn: Ada", "sn: Lovelace");
            server.add("dn: cn=Grace,dc=example,dc=org", "objectClass: person", "cn: Grace", "sn: Hopper");
            server.startListening();
        } catch (Exception ex) {
            server.close();
            throw ex;
        }
    }

    LdapContextSource contextSource() throws Exception {
        LdapContextSource source = new LdapContextSource();
        source.setUrl("ldap://localhost:" + server.getListenPort());
        source.setBase("dc=example,dc=org");
        source.setUserDn("cn=admin");
        source.setPassword("secret");
        source.setBaseEnvironmentProperties(Map.of(
                "com.sun.jndi.ldap.connect.timeout", "10000",
                "com.sun.jndi.ldap.read.timeout", "10000"));
        source.afterPropertiesSet();
        return source;
    }

    LdapContextSource tlsContextSource(SSLContext tls) throws Exception {
        DefaultTlsDirContextAuthenticationStrategy strategy = new DefaultTlsDirContextAuthenticationStrategy();
        strategy.setShutdownTlsGracefully(true);
        strategy.setSslSocketFactory(tls.getSocketFactory());
        LdapContextSource source = contextSource();
        source.setAuthenticationStrategy(strategy);
        return source;
    }

    @Override
    public void close() {
        server.close();
    }
}
