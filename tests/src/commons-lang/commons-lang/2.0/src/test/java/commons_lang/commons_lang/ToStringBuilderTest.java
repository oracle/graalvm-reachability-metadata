/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.junit.jupiter.api.Test;

public class ToStringBuilderTest {

    @Test
    public void reflectionToStringReadsDeclaredFields() {
        DisplayRecord record = new DisplayRecord("alpha", 7);

        String description = ToStringBuilder.reflectionToString(record);

        assertThat(description).contains("name=alpha", "rank=7");
    }

    private static final class DisplayRecord {
        private final String name;
        private final Integer rank;

        private DisplayRecord(String name, Integer rank) {
            this.name = name;
            this.rank = rank;
        }
    }
}
