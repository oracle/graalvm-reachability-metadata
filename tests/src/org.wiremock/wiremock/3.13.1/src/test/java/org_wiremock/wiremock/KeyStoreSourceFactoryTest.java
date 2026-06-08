/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.ssl.KeyStoreSettings;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.jetty11.WritableFileOrClasspathKeyStoreSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KeyStoreSourceFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void browserProxySettingsUseWritableCaKeystoreSourceForCurrentJre() throws Exception {
        String password = "changeit";
        Path keystorePath = temporaryDirectory.resolve("wiremock-ca.jks");
        KeyStoreSettings settings = WireMockConfiguration.options()
                .caKeystorePath(keystorePath.toString())
                .caKeystorePassword(password)
                .caKeystoreType("JKS")
                .browserProxySettings()
                .caKeyStore();

        assertThat(settings.path()).isEqualTo(keystorePath.toString());
        assertThat(settings.type()).isEqualTo("JKS");
        assertThat(settings.password()).isEqualTo(password);
        assertThat(settings.getSource()).isInstanceOf(WritableFileOrClasspathKeyStoreSource.class);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, password.toCharArray());
        settings.getSource().save(keyStore);

        assertThat(Files.isRegularFile(keystorePath)).isTrue();
        assertThat(settings.exists()).isTrue();
    }
}
