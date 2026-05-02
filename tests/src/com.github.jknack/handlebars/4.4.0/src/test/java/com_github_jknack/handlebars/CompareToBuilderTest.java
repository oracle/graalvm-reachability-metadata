/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {
    @Test
    public void reflectionCompareReadsDeclaredFields() {
        Document left = new Document("alpha", 1, "draft");
        Document right = new Document("beta", 2, "published");
        Document sameAsLeft = new Document("alpha", 1, "archived");

        assertThat(CompareToBuilder.reflectionCompare(left, right)).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(left, sameAsLeft)).isZero();
    }

    @Test
    public void reflectionCompareCanIncludeTransientDeclaredFields() {
        Document left = new Document("alpha", 1, "draft");
        Document right = new Document("alpha", 1, "published");

        assertThat(CompareToBuilder.reflectionCompare(left, right)).isZero();
        assertThat(CompareToBuilder.reflectionCompare(left, right, true)).isNegative();
    }

    @Test
    public void reflectionCompareIgnoresExcludedDeclaredFields() {
        Document left = new Document("alpha", 1, "draft");
        Document right = new Document("beta", 2, "draft");

        assertThat(CompareToBuilder.reflectionCompare(left, right, "title", "priority")).isZero();
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
