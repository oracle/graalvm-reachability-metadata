/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Plexus component descriptor cataloging so an application can select a plugin before lookup.
public class PlexusComponentDescriptorCatalogTest {
    @Test
    public void catalogsAvailablePluginsBeforeSelectingOne() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    ReportExporter.class, JsonReportExporter.class, "json"));
            container.addComponentDescriptor(descriptor(
                    ReportExporter.class, TextReportExporter.class, "text"));

            PluginCatalog catalog = new PluginCatalog(container);
            Map<?, ?> descriptors = catalog.descriptorsFor(ReportExporter.class);
            ComponentDescriptor selected = catalog.select(ReportExporter.class, "json");
            ReportExporter exporter = (ReportExporter) container.lookup(
                    ReportExporter.class.getName(), selected.getRoleHint());

            assertEquals(2, descriptors.size());
            assertEquals(JsonReportExporter.class.getName(), selected.getImplementation());
            assertNotNull(descriptors.get("json"));
            assertEquals("{\"name\":\"release\"}", exporter.export("release"));
        } finally {
            container.dispose();
        }
    }

    private static ComponentDescriptor descriptor(
            Class<?> role, Class<?> implementation, String roleHint) {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(role.getName());
        descriptor.setRoleHint(roleHint);
        descriptor.setImplementation(implementation.getName());
        descriptor.setInstantiationStrategy("singleton");
        return descriptor;
    }

    public static final class PluginCatalog {
        private final PlexusContainer container;

        public PluginCatalog(PlexusContainer container) {
            this.container = container;
        }

        public Map<?, ?> descriptorsFor(Class<?> role) {
            return container.getComponentDescriptorMap(role.getName());
        }

        public ComponentDescriptor select(Class<?> role, String roleHint) {
            ComponentDescriptor descriptor = (ComponentDescriptor) descriptorsFor(role).get(roleHint);
            if (descriptor == null) {
                throw new IllegalArgumentException("No plugin for " + roleHint);
            }
            return descriptor;
        }
    }

    public interface ReportExporter {
        String export(String reportName);
    }

    public static final class JsonReportExporter implements ReportExporter {
        @Override
        public String export(String reportName) {
            return "{\"name\":\"" + reportName + "\"}";
        }
    }

    public static final class TextReportExporter implements ReportExporter {
        @Override
        public String export(String reportName) {
            return "REPORT: " + reportName;
        }
    }
}
