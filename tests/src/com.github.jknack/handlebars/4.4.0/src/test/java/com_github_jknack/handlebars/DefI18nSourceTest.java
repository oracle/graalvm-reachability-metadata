/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class DefI18nSourceTest {
    private static final String BUNDLE_NAME =
            "com_github_jknack.handlebars.DefI18nSourceTestMessages";

    @Test
    public void rendersMessageFromResourceBundle() throws IOException {
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline(
                "{{i18n \"welcome\" \"GraalVM\" bundle=\"" + BUNDLE_NAME + "\" locale=\"\"}}");

        String rendered = template.apply(new Object());

        assertThat(rendered).isEqualTo("Welcome GraalVM!");
    }
}
