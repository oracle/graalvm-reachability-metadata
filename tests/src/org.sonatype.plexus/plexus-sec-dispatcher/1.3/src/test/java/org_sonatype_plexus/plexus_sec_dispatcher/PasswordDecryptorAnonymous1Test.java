/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_plexus.plexus_sec_dispatcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.PasswordDecryptor;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

public class PasswordDecryptorAnonymous1Test {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatcherDecryptsPasswordUsingContainerWiredComponents() throws Exception {
        String masterPassword = "master-password";
        String serverPassword = "server-password";
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        Path configurationFile = temporaryDirectory.resolve("settings-security.xml");
        String encryptedMasterPassword = cipher.encryptAndDecorate(masterPassword, "settings.security");
        String encryptedServerPassword = cipher.encryptAndDecorate(serverPassword, masterPassword);
        Files.writeString(
                configurationFile,
                "<settingsSecurity><master>" + encryptedMasterPassword + "</master></settingsSecurity>");

        assertThat(PasswordDecryptor.ROLE).isEqualTo(PasswordDecryptor.class.getName());

        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            container.initialize();
            container.start();
            DefaultSecDispatcher dispatcher = (DefaultSecDispatcher) container.lookup(SecDispatcher.ROLE);
            dispatcher.setConfigurationFile(configurationFile.toString());

            assertThat(dispatcher.decrypt(encryptedServerPassword)).isEqualTo(serverPassword);
        } finally {
            container.dispose();
        }
    }
}
