/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_encryption_plugin;

import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EncryptionPluginManagerTest {
    @Test
    void discoversAndUsesAnApplicationEncryptionService() {
        EncryptionPluginManager manager = EncryptionPluginManager.instance();
        EncryptionPluginService service = manager.findEncryptionService("TestAes").orElseThrow();
        String secretKey = service.generateSecretKey();
        String plaintext = "database.password=private-value";
        String ciphertext = service.encrypt(secretKey, plaintext);
        String wrappedKey = service.encryptSecretKey(secretKey);

        assertEquals("TestAes", service.algorithmName());
        assertNotEquals(plaintext, ciphertext);
        assertNotEquals(secretKey, wrappedKey);
        assertEquals(secretKey, service.decryptSecretKey(wrappedKey));
        assertEquals(plaintext, service.decrypt(secretKey, ciphertext));
        assertSame(service, manager.getAllPlugins().get("TestAes"));
    }

    @Test
    void registersAnApplicationServiceInAReadOnlyPluginView() {
        EncryptionPluginManager manager = EncryptionPluginManager.instance();
        Map<String, EncryptionPluginService> plugins = manager.getAllPlugins();
        EncryptionPluginService service = new AesEncryptionPlugin("JoinedAes");

        EncryptionPluginManager.join(service);

        assertSame(service, manager.findEncryptionService("JoinedAes").orElseThrow());
        assertSame(service, plugins.get("JoinedAes"));
        assertThrows(UnsupportedOperationException.class, () -> plugins.remove("JoinedAes"));
        assertSame(service, manager.findEncryptionService("JoinedAes").orElseThrow());
    }

    @Test
    void returnsNoServiceForAnUnregisteredAlgorithm() {
        assertTrue(EncryptionPluginManager.instance().findEncryptionService("UnregisteredAlgorithm").isEmpty());
    }
}
