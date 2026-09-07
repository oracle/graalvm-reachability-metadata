/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorInnerConstructSequenceTest {
    @Test
    void choosesConstructorsForSequenceValues() {
        Yaml yaml = new Yaml();
        Pair pair = yaml.loadAs("[left, 4]", Pair.class);
        Sequence sequence = yaml.loadAs("[[one, two]]", Sequence.class);
        OverloadedNumber overloadedNumber = yaml.loadAs("[7]", OverloadedNumber.class);

        assertThat(pair.left).isEqualTo("left");
        assertThat(pair.right).isEqualTo(4);
        assertThat(sequence.values).containsExactly("one", "two");
        assertThat(overloadedNumber.value).isEqualTo(7);
    }

    public static class Pair {
        private final String left;
        private final int right;

        public Pair(String left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    public static class Sequence {
        private final List<String> values;

        public Sequence(List<String> values) {
            this.values = values;
        }
    }

    public static class OverloadedNumber {
        private final int value;

        public OverloadedNumber(Integer value) {
            this.value = value;
        }

        public OverloadedNumber(String value) {
            this.value = Integer.parseInt(value);
        }
    }
}
