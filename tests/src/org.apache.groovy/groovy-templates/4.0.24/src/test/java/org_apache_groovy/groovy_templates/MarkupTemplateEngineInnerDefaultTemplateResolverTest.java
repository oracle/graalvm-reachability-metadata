/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_templates;

import groovy.text.markup.MarkupTemplateEngine.DefaultTemplateResolver;
import groovy.text.markup.TemplateConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class MarkupTemplateEngineInnerDefaultTemplateResolverTest {
    @Test
    void explicitLocaleTakesPrecedenceOverConfiguredLocale() throws IOException {
        DefaultTemplateResolver resolver = newResolver();

        URL template = resolver.resolveTemplate("templates/resolver/welcome_fr_FR.tpl");

        assertEquals("p('Bonjour')\n", readTemplate(template));
    }

    @Test
    void unqualifiedPathUsesConfiguredLocale() throws IOException {
        DefaultTemplateResolver resolver = newResolver();

        URL template = resolver.resolveTemplate("templates/resolver/welcome.tpl");

        assertEquals("p('Welcome')\n", readTemplate(template));
    }

    @Test
    void missingExplicitLocaleFallsBackToConfiguredLocale() throws IOException {
        DefaultTemplateResolver resolver = newResolver();

        URL template = resolver.resolveTemplate("templates/resolver/welcome_de_DE.tpl");

        assertEquals("p('Welcome')\n", readTemplate(template));
    }

    @Test
    void missingLocalizedTemplatesFallBackToUnqualifiedResource() throws IOException {
        DefaultTemplateResolver resolver = newResolver();

        URL template = resolver.resolveTemplate("templates/resolver/plain_fr_FR.tpl");

        assertEquals("p('Default page')\n", readTemplate(template));
    }

    private static DefaultTemplateResolver newResolver() {
        TemplateConfiguration configuration = new TemplateConfiguration();
        configuration.setLocale(Locale.US);
        DefaultTemplateResolver resolver = new DefaultTemplateResolver();
        resolver.configure(MarkupTemplateEngineInnerDefaultTemplateResolverTest.class.getClassLoader(), configuration);
        return resolver;
    }

    private static String readTemplate(URL template) throws IOException {
        try (InputStream input = template.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
