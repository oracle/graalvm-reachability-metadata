/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.Handlebars;
import org.junit.jupiter.api.Test;

public class HandlebarsTest {
    private static final String CUSTOM_HANDLEBARS_JS = "/custom-handlebars.js";

    @Test
    public void configuresCustomHandlebarsJsResource() {
        Handlebars handlebars = new Handlebars();

        Handlebars configured = handlebars.handlebarsJsFile(CUSTOM_HANDLEBARS_JS);

        assertThat(configured).isSameAs(handlebars);
        assertThat(handlebars.handlebarsJsFile()).isEqualTo(CUSTOM_HANDLEBARS_JS);
    }
}
