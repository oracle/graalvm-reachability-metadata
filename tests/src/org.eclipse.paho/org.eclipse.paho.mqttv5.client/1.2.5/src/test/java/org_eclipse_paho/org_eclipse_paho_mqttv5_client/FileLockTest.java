/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_mqttv5_client;

import org.eclipse.paho.mqttv5.client.persist.MqttDefaultFilePersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FileLockTest {
    @TempDir
    Path persistenceDirectory;

    @Test
    void filePersistenceReopensAndReleasesItsClientLock() throws Exception {
        MqttDefaultFilePersistence persistence =
                new MqttDefaultFilePersistence(persistenceDirectory.toString());
        Path clientDirectory = persistenceDirectory.resolve("client-with-file-lock");

        try {
            persistence.open("client-with-file-lock");
            assertThat(clientDirectory.resolve(".lck")).exists();

            persistence.open("client-with-file-lock");
            assertThat(clientDirectory.resolve(".lck")).exists();
        } finally {
            persistence.close();
        }

        assertThat(Files.exists(clientDirectory.resolve(".lck"))).isFalse();
    }
}
