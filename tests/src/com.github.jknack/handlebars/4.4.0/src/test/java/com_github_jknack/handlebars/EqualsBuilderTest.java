/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.internal.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {
    @Test
    public void reflectionEqualsReadsDeclaredFields() {
        Document left = new Document("guide", 2, "draft");
        Document sameAsLeft = new Document("guide", 2, "published");
        Document different = new Document("guide", 3, "draft");

        assertThat(EqualsBuilder.reflectionEquals(left, sameAsLeft)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(left, different)).isFalse();
    }

    @Test
    public void reflectionEqualsCanIncludeTransientDeclaredFields() {
        Document left = new Document("guide", 2, "draft");
        Document right = new Document("guide", 2, "published");

        assertThat(EqualsBuilder.reflectionEquals(left, right)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(left, right, true)).isFalse();
    }

    @Test
    public void reflectionEqualsIgnoresExcludedDeclaredFields() {
        Document left = new Document("guide", 2, "draft");
        Document right = new Document("guide", 3, "draft");

        assertThat(EqualsBuilder.reflectionEquals(left, right, "priority")).isTrue();
    }

    private static final class Document {
        private static final String TYPE = "document";

        private final String title;
        private final int priority;
        private transient String state;

        private Document(String title, int priority, String state) {
            this.title = title;
            this.priority = priority;
            this.state = state;
        }
    }
}
