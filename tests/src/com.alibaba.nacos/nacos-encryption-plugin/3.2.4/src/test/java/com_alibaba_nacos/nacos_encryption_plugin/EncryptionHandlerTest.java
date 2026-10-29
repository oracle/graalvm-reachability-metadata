/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_encryption_plugin;

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EncryptionHandlerTest {
    @Test
    void encryptsConfigurationAndWrapsItsKeyForLaterDecryption() {
        String dataId = "cipher-TestAes-application-settings.yml";
        String plaintext = """
                database:
                  username: application
                  password: private-value
                """;
        Pair<String, String> encrypted = EncryptionHandler.encryptHandler(dataId, plaintext);
        EncryptionPluginService service = EncryptionPluginManager.instance()
                .findEncryptionService("TestAes").orElseThrow();
        String secretKey = service.decryptSecretKey(encrypted.getFirst());

        assertFalse(secretKey.isEmpty());
        assertNotEquals(secretKey, encrypted.getFirst());
        assertNotEquals(plaintext, encrypted.getSecond());
        assertEquals(plaintext, service.decrypt(secretKey, encrypted.getSecond()));

        Pair<String, String> decrypted = EncryptionHandler.decryptHandler(dataId,
                encrypted.getFirst(), encrypted.getSecond());
        assertEquals(secretKey, decrypted.getFirst());
        assertEquals(plaintext, decrypted.getSecond());
    }

    @Test
    void preservesConfigurationThatDoesNotSelectEncryption() {
        for (String dataId : new String[]{"application.yml", "", "cipher-"}) {
            Pair<String, String> encrypted = EncryptionHandler.encryptHandler(dataId, "plain configuration");
            assertEquals("", encrypted.getFirst());
            assertEquals("plain configuration", encrypted.getSecond());

            Pair<String, String> decrypted = EncryptionHandler.decryptHandler(dataId,
                    "existing-key", "plain configuration");
            assertEquals("existing-key", decrypted.getFirst());
            assertEquals("plain configuration", decrypted.getSecond());
        }
    }

    @Test
    void preservesConfigurationWhenNoMatchingPluginIsInstalled() {
        String dataId = "cipher-UnregisteredAlgorithm-application.yml";
        Pair<String, String> encrypted = EncryptionHandler.encryptHandler(dataId, "configuration");
        assertEquals("", encrypted.getFirst());
        assertEquals("configuration", encrypted.getSecond());

        Pair<String, String> decrypted = EncryptionHandler.decryptHandler(dataId,
                "stored-key", "stored-ciphertext");
        assertEquals("stored-key", decrypted.getFirst());
        assertEquals("stored-ciphertext", decrypted.getSecond());
    }
}
