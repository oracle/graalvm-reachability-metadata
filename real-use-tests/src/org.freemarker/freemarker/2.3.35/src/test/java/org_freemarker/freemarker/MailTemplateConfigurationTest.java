/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.ConditionalTemplateConfigurationFactory;
import freemarker.cache.FileNameGlobMatcher;
import freemarker.cache.StringTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateConfiguration;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's template-configuration factory subsystem to render branded mail.
public class MailTemplateConfigurationTest {
    @Test
    @Timeout(60)
    public void appliesMailTemplatePolicyByFileName() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("mail/welcome.mail", "<h1>${title}</h1><p>${body}</p>");

        TemplateConfiguration mailPolicy = new TemplateConfiguration();
        mailPolicy.setOutputFormat(HTMLOutputFormat.INSTANCE);
        mailPolicy.setAutoEscapingPolicy(Configuration.ENABLE_IF_DEFAULT_AUTO_ESCAPING_POLICY);

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateConfigurations(new ConditionalTemplateConfigurationFactory(
                new FileNameGlobMatcher("*.mail"), mailPolicy));
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("mail/welcome.mail");
        StringWriter rendered = new StringWriter();
        template.process(Map.of(
                "title", "Welcome & thanks",
                "body", "Use <strong>secure</strong> sign-in."), rendered);

        assertEquals("<h1>Welcome &amp; thanks</h1><p>Use &lt;strong&gt;secure&lt;/strong&gt; sign-in.</p>",
                rendered.toString());
    }
}
