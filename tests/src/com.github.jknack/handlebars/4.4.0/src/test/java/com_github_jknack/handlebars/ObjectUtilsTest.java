/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.internal.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {
    @Test
    public void cloneCopiesPrimitiveArrays() {
        int[] original = new int[] {1, 2, 3};

        int[] cloned = ObjectUtils.clone(original);

        assertThat(cloned).containsExactly(1, 2, 3);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    public void cloneInvokesPublicCloneMethodOnCloneableObjects() {
        CopyableDocument original = new CopyableDocument("title", new StringBuilder("body"));

        CopyableDocument cloned = ObjectUtils.clone(original);

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.title()).isEqualTo("title");
        assertThat(cloned.body()).hasToString("body");
        assertThat(cloned.body()).isNotSameAs(original.body());
    }

    public static final class CopyableDocument implements Cloneable {
        private final String title;
        private final StringBuilder body;

        public CopyableDocument(String title, StringBuilder body) {
            this.title = title;
            this.body = body;
        }

        public String title() {
            return title;
        }

        public StringBuilder body() {
            return body;
        }

        @Override
        public CopyableDocument clone() {
            return new CopyableDocument(title, new StringBuilder(body));
        }
    }
}
