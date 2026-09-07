/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.hamcrest.object.HasEqualValues;

import static org.assertj.core.api.Assertions.assertThat;

public class HasEqualValuesTest {
    @Test
    void comparesPublicFieldValues() {
        HasEqualValues<Fields> matcher = new HasEqualValues<>(new Fields("same", 3));

        assertThat(matcher.matches(new Fields("same", 3))).isTrue();
        assertThat(matcher.matches(new Fields("different", 3))).isFalse();
    }

    public static class Fields {
        public String text;
        public int count;

        public Fields(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }
}
