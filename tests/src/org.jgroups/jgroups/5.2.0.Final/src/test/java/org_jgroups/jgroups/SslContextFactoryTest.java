/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jgroups.util.SslContextFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SslContextFactoryTest {
    @Test
    void initializesSslContextAndEngineUsingPublicFactoryApi() throws Exception {
        configureWildFlyOpenSslNativeLibrary();

        String defaultProtocol = SslContextFactory.getDefaultSslProtocol();
        String providerName = SSLContext.getInstance(defaultProtocol).getProvider().getName();
        assertThat(defaultProtocol).isEqualTo("TLSv1.2");

        SSLContext context = new SslContextFactory()
                .sslProvider(providerName)
                .sslProtocol(defaultProtocol)
                .getContext();
        SSLEngine engine = SslContextFactory.getEngine(context, true, false);

        assertThat(context.getProtocol()).isEqualTo(defaultProtocol);
        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getNeedClientAuth()).isFalse();
    }

    private static void configureWildFlyOpenSslNativeLibrary() throws Exception {
        Path nativeLibrary = Files.createTempFile("wildfly-openssl-", ".so");
        nativeLibrary.toFile().deleteOnExit();
        try (InputStream stream = SslContextFactoryTest.class.getClassLoader()
                .getResourceAsStream("linux-x86_64/libwfssl.so")) {
            if (stream == null) {
                return;
            }
            Files.copy(stream, nativeLibrary, StandardCopyOption.REPLACE_EXISTING);
        }
        System.setProperty("org.wildfly.openssl.libwfssl.path", nativeLibrary.toString());
    }
}
