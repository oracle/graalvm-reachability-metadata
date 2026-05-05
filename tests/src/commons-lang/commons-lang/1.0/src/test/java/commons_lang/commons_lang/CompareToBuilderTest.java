/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {

    @Test
    public void reflectionCompareReadsDeclaredFields() {
        ComparableRecord earlier = new ComparableRecord("alpha", 1);
        ComparableRecord later = new ComparableRecord("alpha", 2);

        int comparison = CompareToBuilder.reflectionCompare(earlier, later);

        assertThat(comparison).isLessThan(0);
    }

    private static final class ComparableRecord {
        private final String name;
        private final Integer rank;

        private ComparableRecord(String name, Integer rank) {
            this.name = name;
            this.rank = rank;
        }
    }
}
