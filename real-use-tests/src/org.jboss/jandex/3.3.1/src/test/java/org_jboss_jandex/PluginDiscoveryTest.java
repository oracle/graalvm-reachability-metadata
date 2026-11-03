/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jandex's type hierarchy subsystem to discover application plugins.
public class PluginDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversDirectAndIndirectPlugins() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(Plugin.class);
        indexer.indexClass(PaymentPlugin.class);
        indexer.indexClass(ShippingPlugin.class);
        indexer.indexClass(AbstractPlugin.class);
        indexer.indexClass(InventoryPlugin.class);
        Index index = indexer.complete();

        Set<ClassInfo> directPlugins = Set.copyOf(index.getKnownDirectImplementors(Plugin.class));
        List<String> directPluginNames = directPlugins.stream()
                .map(classInfo -> classInfo.name().toString())
                .sorted()
                .toList();
        List<String> allPluginNames = index.getAllKnownImplementors(Plugin.class).stream()
                .map(ClassInfo::name)
                .map(Object::toString)
                .sorted()
                .toList();

        assertEquals(List.of(
                AbstractPlugin.class.getName(),
                PaymentPlugin.class.getName()), directPluginNames);
        assertEquals(List.of(
                AbstractPlugin.class.getName(),
                InventoryPlugin.class.getName(),
                PaymentPlugin.class.getName(),
                ShippingPlugin.class.getName()), allPluginNames);
    }

    private interface Plugin {
    }

    private static final class PaymentPlugin implements Plugin {
    }

    private static class ShippingPlugin extends AbstractPlugin {
    }

    private abstract static class AbstractPlugin implements Plugin {
    }

    private static final class InventoryPlugin extends ShippingPlugin {
    }
}
