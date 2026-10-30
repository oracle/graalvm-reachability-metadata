/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginProvider;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuthPluginProviderTest {
    @Test
    void discoversTheAuthenticationProviderThroughThePublicPluginSpi() {
        AuthPluginProvider provider = ServiceLoader.load(PluginProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(AuthPluginProvider.class::isInstance)
                .map(AuthPluginProvider.class::cast)
                .findFirst().orElseThrow();

        assertEquals(PluginType.AUTH, provider.getPluginType());
        assertEquals(AuthPluginManager.getInstance().getAllPlugins(), provider.getAllPlugins());
    }

    @Test
    void exposesAReadOnlyRegistryAndDoesNotResolveUnknownPlugins() {
        AuthPluginManager manager = AuthPluginManager.getInstance();
        Map<String, AuthPluginService> plugins = manager.getAllPlugins();

        assertTrue(manager.findAuthServiceSpiImpl("not-installed-auth-provider").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plugins.remove("not-installed-auth-provider"));
    }
}
