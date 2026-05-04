/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.value.CompareMode;
import org.junit.jupiter.api.Test;

import java.text.Collator;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareModeIcu4JTest {
    @Test
    void comparesStringsWithExplicitIcu4JCollator() {
        CompareMode mode = CompareMode.getInstance(CompareMode.ICU4J + "en", Collator.TERTIARY);

        assertThat(mode.compareString("abc", "abd", false)).isLessThan(0);
        assertThat(mode.compareString("abc", "ABC", true)).isZero();
    }
}
