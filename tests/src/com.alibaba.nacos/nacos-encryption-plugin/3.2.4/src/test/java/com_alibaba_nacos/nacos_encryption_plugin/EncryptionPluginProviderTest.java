/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_encryption_plugin;

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginProvider;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EncryptionPluginProviderTest {
    @Test
    void discoversTheEncryptionProviderAndItsRegisteredServices() {
        EncryptionPluginProvider provider = NacosServiceLoader.load(PluginProvider.class).stream()
                .filter(EncryptionPluginProvider.class::isInstance)
                .map(EncryptionPluginProvider.class::cast)
                .findFirst().orElseThrow();
        EncryptionPluginService service = provider.getAllPlugins().get("TestAes");

        assertEquals(PluginType.ENCRYPTION, provider.getPluginType());
        assertSame(EncryptionPluginManager.instance().findEncryptionService("TestAes").orElseThrow(), service);
        String key = service.generateSecretKey();
        assertEquals("provider configuration", service.decrypt(key, service.encrypt(key, "provider configuration")));
        assertThrows(UnsupportedOperationException.class, () -> provider.getAllPlugins().clear());
    }
}
