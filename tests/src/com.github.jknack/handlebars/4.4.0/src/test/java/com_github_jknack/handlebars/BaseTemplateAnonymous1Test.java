/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.TypeSafeTemplate;
import org.junit.jupiter.api.Test;

public class BaseTemplateAnonymous1Test {
    @Test
    public void invokesDefaultMethodOnTypeSafeTemplateProxy() throws IOException {
        Template template = new Handlebars().compileInline("{{prefix}} {{name}}");
        DefaultMethodTemplate defaultMethodTemplate = template.as(DefaultMethodTemplate.class);

        String rendered = defaultMethodTemplate.applyWithDefaultPrefix(Collections.singletonMap("name", "Ada"));

        assertThat(rendered).isEqualTo("Default Ada");
    }

    public interface DefaultMethodTemplate extends TypeSafeTemplate<Map<String, String>> {
        DefaultMethodTemplate setPrefix(String prefix);

        default String applyWithDefaultPrefix(Map<String, String> context) throws IOException {
            return setPrefix("Default").apply(context);
        }
    }
}
