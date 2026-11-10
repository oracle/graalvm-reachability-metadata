/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_ldap;

import java.net.InetAddress;
import java.util.Map;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

final class LdapTestServer implements AutoCloseable {

    static final String BASE = "dc=example,dc=org";
    static final String USERNAME = "cn=manager";
    static final String PASSWORD = "directory-secret";

    private final InMemoryDirectoryServer server;

    LdapTestServer() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                "ldap", InetAddress.getLoopbackAddress(), 0, null));
        config.addAdditionalBindCredentials(USERNAME, PASSWORD);
        server = new InMemoryDirectoryServer(config);
        try {
            server.add(new Entry("""
                    dn: dc=example,dc=org
                    objectClass: domain
                    dc: example
                    """.split("\\R")));
            server.add(new Entry("""
                    dn: cn=Ada,dc=example,dc=org
                    objectClass: person
                    cn: Ada
                    sn: Lovelace
                    """.split("\\R")));
            server.startListening();
        } catch (Exception ex) {
            server.close();
            throw ex;
        }
    }

    String url() {
        return "ldap://localhost:" + server.getListenPort();
    }

    LdapTemplate template(String password) throws Exception {
        LdapContextSource source = new LdapContextSource();
        source.setUrl(url());
        source.setBase(BASE);
        source.setUserDn(USERNAME);
        source.setPassword(password);
        source.setBaseEnvironmentProperties(Map.of(
                "com.sun.jndi.ldap.connect.timeout", "10000",
                "com.sun.jndi.ldap.read.timeout", "10000",
                "java.naming.ldap.version", "3"));
        source.afterPropertiesSet();
        return new LdapTemplate(source);
    }

    @Override
    public void close() {
        server.close();
    }
}
