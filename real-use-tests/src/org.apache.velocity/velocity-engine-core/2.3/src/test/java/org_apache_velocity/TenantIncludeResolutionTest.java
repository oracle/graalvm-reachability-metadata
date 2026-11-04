/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.IncludeEventHandler;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's include event subsystem for tenant-specific report fragments.
public class TenantIncludeResolutionTest {
    @Test
    @Timeout(60)
    public void resolvesTenantFragmentsBeforeRenderingReport() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty("resource.loader", "string");
        engine.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.init();

        StringResourceRepository repository = StringResourceLoader.getRepository();
        repository.putStringResource("tenant-header.vm", "Tenant: Northwind\n");
        repository.putStringResource("tenant-footer.vm", "Status: ready\n");
        try {
            VelocityContext context = new VelocityContext();
            context.put("tenant", "Northwind");
            context.put("status", "ready");
            EventCartridge eventCartridge = new EventCartridge();
            eventCartridge.addEventHandler(new TenantIncludeHandler());
            eventCartridge.attachToContext(context);

            StringWriter report = new StringWriter();
            boolean rendered = engine.evaluate(
                    context,
                    report,
                    "tenant-report",
                    "#include('header.vm')#include('footer.vm')\nReport owner: $tenant");

            assertTrue(rendered);
            assertEquals("Tenant: Northwind\nStatus: ready\nReport owner: Northwind", report.toString());
        } finally {
            repository.removeStringResource("tenant-header.vm");
            repository.removeStringResource("tenant-footer.vm");
        }
    }

    public static final class TenantIncludeHandler implements IncludeEventHandler {
        @Override
        public String includeEvent(Context context, String includeResourcePath,
                String currentResourcePath, String directiveName) {
            if ("header.vm".equals(includeResourcePath)) {
                return "tenant-header.vm";
            }
            if ("footer.vm".equals(includeResourcePath)) {
                return "tenant-footer.vm";
            }
            return includeResourcePath;
        }
    }
}
