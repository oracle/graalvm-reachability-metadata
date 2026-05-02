/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.internal.lang3.builder.ReflectionToStringBuilder;
import com.github.jknack.handlebars.internal.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {
    @Test
    public void toStringReadsDeclaredFields() {
        Release release = new Release("handlebars", 7, "draft", null);

        String description = ReflectionToStringBuilder.toString(release, ToStringStyle.SHORT_PREFIX_STYLE, true, false);

        assertThat(description)
                .contains("name=handlebars")
                .contains("priority=7")
                .contains("state=draft")
                .contains("owner=<null>");
    }

    @Test
    public void toStringCanExcludeNullAndNamedFields() {
        Release release = new Release("handlebars", 7, "draft", null);

        ReflectionToStringBuilder builder = new ReflectionToStringBuilder(release, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.setAppendTransients(true);
        builder.setExcludeNullValues(true);
        builder.setExcludeFieldNames("priority");

        String description = builder.toString();

        assertThat(description)
                .contains("name=handlebars")
                .contains("state=draft")
                .doesNotContain("priority")
                .doesNotContain("owner");
    }

    private static final class Release {
        private final String name;
        private final int priority;
        private transient String state;
        private final String owner;

        private Release(String name, int priority, String state, String owner) {
            this.name = name;
            this.priority = priority;
            this.state = state;
            this.owner = owner;
        }
    }
}
