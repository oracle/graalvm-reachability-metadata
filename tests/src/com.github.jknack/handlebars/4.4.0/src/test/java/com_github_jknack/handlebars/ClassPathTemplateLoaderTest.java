/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ClassPathTemplateLoaderTest {
    private static final String TEMPLATE_LOCATION = "/templates/classpath-loader/welcome.hbs";

    @Test
    public void loadsTemplateSourceFromClasspathResource() throws IOException {
        ClassPathTemplateLoader loader = new ClassPathTemplateLoader("/templates/classpath-loader");

        TemplateSource source = loader.sourceAt("welcome");

        assertThat(source.filename()).isEqualTo(TEMPLATE_LOCATION);
        assertThat(source.content(StandardCharsets.UTF_8)).contains("Hello {{name}} from ClassPathTemplateLoader!");
    }
}
