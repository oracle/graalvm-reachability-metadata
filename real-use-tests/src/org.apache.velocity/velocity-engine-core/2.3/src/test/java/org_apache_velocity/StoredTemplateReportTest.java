/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's string resource loading subsystem for application-managed reports.
public class StoredTemplateReportTest {
    @Test
    @Timeout(60)
    public void rendersReportFromTemplateStoredInRepository() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty("resource.loader", "string");
        engine.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.init();

        StringResourceRepository repository = StringResourceLoader.getRepository();
        repository.putStringResource(
                "daily-report.vm",
                "Daily report for $account\n"
                        + "#foreach($entry in $entries)$entry.label=$entry.value\n#end\n"
                        + "Total: $total");
        try {
            VelocityContext context = new VelocityContext();
            context.put("account", "Northwind");
            context.put("entries", java.util.List.of(
                    new ReportEntry("orders", 18),
                    new ReportEntry("returns", 2)));
            context.put("total", 20);

            assertTrue(engine.resourceExists("daily-report.vm"));
            StringWriter report = new StringWriter();
            engine.getTemplate("daily-report.vm", "UTF-8").merge(context, report);

            assertEquals("Daily report for Northwind\norders=18\nreturns=2\nTotal: 20", report.toString());
        } finally {
            repository.removeStringResource("daily-report.vm");
        }
    }

    public static final class ReportEntry {
        private final String label;
        private final int value;

        public ReportEntry(String label, int value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public int getValue() {
            return value;
        }
    }
}
