/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.ByteArrayTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's composite template-loading subsystem for tenant-specific storefront pages.
public class TenantTemplateLoadingTest {
    @Test
    @Timeout(60)
    public void loadsTenantOverrideAndSharedFallbackTemplates() throws Exception {
        ByteArrayTemplateLoader tenantTemplates = new ByteArrayTemplateLoader();
        tenantTemplates.putTemplate("dashboard.ftl", "<h1>${tenant}</h1>\n"
                .concat("<p>Welcome to the Acme storefront.</p>").getBytes(StandardCharsets.UTF_8));

        StringTemplateLoader sharedTemplates = new StringTemplateLoader();
        sharedTemplates.putTemplate("dashboard.ftl", "<h1>${tenant}</h1>\n"
                + "<p>Welcome to the storefront.</p>");
        sharedTemplates.putTemplate("legal.ftl", "<footer>${company} terms apply.</footer>");

        MultiTemplateLoader templateLoaders = new MultiTemplateLoader(new TemplateLoader[] {
                tenantTemplates,
                sharedTemplates
        });
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templateLoaders);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template dashboard = configuration.getTemplate("dashboard.ftl");
        StringWriter dashboardOutput = new StringWriter();
        dashboard.process(Map.of("tenant", "Acme"), dashboardOutput);

        Template legal = configuration.getTemplate("legal.ftl");
        StringWriter legalOutput = new StringWriter();
        legal.process(Map.of("company", "Acme"), legalOutput);

        assertEquals("<h1>Acme</h1>\n<p>Welcome to the Acme storefront.</p>",
                dashboardOutput.toString());
        assertEquals("<footer>Acme terms apply.</footer>", legalOutput.toString());
    }
}
