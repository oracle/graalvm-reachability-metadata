/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.util.List;

import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jandex's composite index subsystem to assemble an application plugin registry from separate artifacts.
public class CompositeIndexTest {
    @Test
    @Timeout(60)
    public void combinesPluginMetadataFromSeparateArtifacts() throws IOException {
        Index platformIndex = index(Plugin.class, PlatformPlugin.class);
        Index billingIndex = index(Plugin.class, BillingPlugin.class);
        Index shippingIndex = index(Plugin.class, ShippingPlugin.class);

        CompositeIndex platformAndBilling = CompositeIndex.create(platformIndex, billingIndex);
        CompositeIndex applicationIndex = CompositeIndex.createMerged(
                platformAndBilling, CompositeIndex.create(shippingIndex));

        List<String> pluginNames = applicationIndex.getAllKnownImplementors(
                        DotName.createSimple(Plugin.class))
                .stream()
                .map(plugin -> plugin.name().toString())
                .sorted()
                .toList();

        assertEquals(List.of(
                BillingPlugin.class.getName(),
                PlatformPlugin.class.getName(),
                ShippingPlugin.class.getName()), pluginNames);
        assertEquals(List.of(
                BillingPlugin.class.getName(),
                PlatformPlugin.class.getName(),
                Plugin.class.getName(),
                ShippingPlugin.class.getName()), applicationIndex.getKnownClasses().stream()
                .map(classInfo -> classInfo.name().toString())
                .distinct()
                .sorted()
                .toList());
    }

    private static Index index(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : classes) {
            indexer.indexClass(type);
        }
        return indexer.complete();
    }

    private interface Plugin {
    }

    private static final class PlatformPlugin implements Plugin {
    }

    private static final class BillingPlugin implements Plugin {
    }

    private static final class ShippingPlugin implements Plugin {
    }
}
