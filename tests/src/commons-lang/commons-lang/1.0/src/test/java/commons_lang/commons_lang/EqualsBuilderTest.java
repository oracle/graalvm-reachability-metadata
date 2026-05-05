/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    public void reflectionEqualsReadsDeclaredFields() {
        ValueRecord left = new ValueRecord("alpha", 1);
        ValueRecord same = new ValueRecord("alpha", 1);
        ValueRecord different = new ValueRecord("alpha", 2);

        assertThat(EqualsBuilder.reflectionEquals(left, same)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(left, different)).isFalse();
    }

    private static final class ValueRecord {
        private final String name;
        private final Integer rank;

        private ValueRecord(String name, Integer rank) {
            this.name = name;
            this.rank = rank;
        }
    }
}
