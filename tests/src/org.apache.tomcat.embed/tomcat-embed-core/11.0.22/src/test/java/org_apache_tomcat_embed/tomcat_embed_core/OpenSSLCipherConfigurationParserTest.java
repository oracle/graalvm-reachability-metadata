/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Set;

import org.apache.catalina.core.OpenSSLLifecycleListener;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(Integer.MAX_VALUE)
public class OpenSSLCipherConfigurationParserTest {

    @Test
    void parsesProfileAndDefaultCipherExpressions() {
        new OpenSSLLifecycleListener();
        boolean openSslAvailable = OpenSSLLifecycleListener.isAvailable();

        Set<Cipher> profileCiphers = OpenSSLCipherConfigurationParser.parse("PROFILE=SYSTEM");
        Set<Cipher> defaultCiphers = OpenSSLCipherConfigurationParser.parse("DEFAULT");

        assertThat(defaultCiphers).isNotEmpty();
        assertThat(profileCiphers).doesNotContainNull();
        assertThat(OpenSSLStatus.isAvailable()).isEqualTo(openSslAvailable);
    }
}
