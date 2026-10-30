/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectUtilsTest {
    @Test
    void copiesPrimitiveArraysAndCloneableObjectsIndependently() {
        int[] original = {3, 5, 8};
        int[] copy = ObjectUtils.clone(original);
        assertThat(copy).containsExactly(3, 5, 8).isNotSameAs(original);
        copy[0] = 13;
        assertThat(original).containsExactly(3, 5, 8);

        Label label = new Label("original");
        Label cloned = ObjectUtils.clone(label);
        assertThat(cloned).isNotSameAs(label);
        assertThat(cloned.text).isEqualTo("original");
        cloned.text = "changed";
        assertThat(label.text).isEqualTo("original");
    }

    public static class Label implements Cloneable {
        private String text;

        public Label(String text) {
            this.text = text;
        }

        @Override
        public Label clone() {
            return new Label(text);
        }
    }
}
