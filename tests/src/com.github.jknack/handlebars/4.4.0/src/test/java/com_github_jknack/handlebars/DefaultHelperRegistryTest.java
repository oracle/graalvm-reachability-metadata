/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.helper.DefaultHelperRegistry;
import org.junit.jupiter.api.Test;

public class DefaultHelperRegistryTest {
    @Test
    public void registerHelpersDiscoversPublicMethodsOnHelperSource() throws IOException {
        DefaultHelperRegistry registry = new DefaultHelperRegistry();

        registry.registerHelpers(new TextHelpers());

        Helper<Object> helper = registry.helper("bracket");
        assertThat(helper).isNotNull();
        assertThat(helper.apply("ready", null)).isEqualTo("[ready]");
    }

    public static final class TextHelpers {
        public String bracket(Object value) {
            return "[" + value + "]";
        }
    }
}
