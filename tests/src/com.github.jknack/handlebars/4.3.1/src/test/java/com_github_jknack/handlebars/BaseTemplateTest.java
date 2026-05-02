/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.TypeSafeTemplate;
import org.junit.jupiter.api.Test;

public class BaseTemplateTest {
    @Test
    public void createsTypeSafeTemplateProxyForCustomTemplateInterface() throws IOException {
        Template template = new Handlebars().compileInline("{{salutation}}, {{name}}!");

        GreetingTemplate greetingTemplate = template.as(GreetingTemplate.class);
        GreetingTemplate configuredTemplate = greetingTemplate.setSalutation("Hello");

        assertThat(configuredTemplate).isSameAs(greetingTemplate);
        assertThat(greetingTemplate)
                .hasToString("TypeSafeTemplateProxy{interface=GreetingTemplate}");
        assertThat(greetingTemplate.apply(Collections.singletonMap("name", "Ada")))
                .isEqualTo("Hello, Ada!");
    }

    @Test
    public void appliesTypeSafeTemplateProxyToWriter() throws IOException {
        Template template = new Handlebars().compileInline("{{salutation}}, {{name}}!");
        GreetingTemplate greetingTemplate = template.as(GreetingTemplate.class);
        Writer writer = new StringWriter();

        greetingTemplate.setSalutation("Welcome")
                .apply(Collections.singletonMap("name", "Grace"), writer);

        assertThat(writer.toString()).isEqualTo("Welcome, Grace!");
    }

    public interface GreetingTemplate extends TypeSafeTemplate<Object> {
        GreetingTemplate setSalutation(String salutation);
    }
}
