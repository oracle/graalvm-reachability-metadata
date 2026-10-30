/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.baseadaptor.HookRegistry;
import org.eclipse.osgi.internal.baseadaptor.BaseHookConfigurator;
import org.eclipse.osgi.internal.baseadaptor.BaseStorage;
import org.eclipse.osgi.internal.baseadaptor.BaseStorageHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseStorageTest {
    @Test
    public void configuresTheInitialBundleStartLevel() {
        HookRegistry registry = new HookRegistry(null);
        new BaseHookConfigurator().addHooks(registry);
        BaseStorage storage = ((BaseStorageHook) registry.getStorageHooks()[0]).getStorage();
        assertEquals(1, storage.getInitialBundleStartLevel());
        storage.setInitialBundleStartLevel(5);
        assertEquals(5, storage.getInitialBundleStartLevel());
    }
}
