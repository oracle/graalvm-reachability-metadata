/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.baseadaptor.HookRegistry;
import org.eclipse.osgi.internal.baseadaptor.BaseHookConfigurator;
import org.eclipse.osgi.internal.baseadaptor.BaseStorageHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HookRegistryTest {
    @Test
    public void loadsTheConfiguredHooksAndFreezesTheRegistry() {
        try (FrameworkProperty configurators = new FrameworkProperty(HookRegistry.PROP_HOOK_CONFIGURATORS,
                BaseHookConfigurator.class.getName())) {
            HookRegistry registry = new HookRegistry(null);
            assertEquals(0, registry.initialize().length);
            assertEquals(1, registry.getStorageHooks().length);
            assertInstanceOf(BaseStorageHook.class, registry.getStorageHooks()[0]);
            assertEquals(1, registry.getClassLoadingHooks().length);
            assertThrows(IllegalStateException.class, () -> new BaseHookConfigurator().addHooks(registry));
        }
    }
}
