/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.value.CompareMode;
import org.junit.jupiter.api.Test;

import java.text.Collator;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareModeIcu4JTest {
    @Test
    void createsIcu4JCollatorsForDirectAndDiscoveredLocaleNames() {
        CompareMode english = CompareMode.getInstance(CompareMode.ICU4J + "en", Collator.TERTIARY);
        CompareMode englishUnitedStates = CompareMode.getInstance(CompareMode.ICU4J + "en_US", Collator.TERTIARY);
        CompareMode discoveredEnglish = CompareMode.getInstance(CompareMode.ICU4J + "ENGLISH", Collator.SECONDARY);

        assertThat(english.compareString("resume", "résumé", false)).isNotZero();
        assertThat(englishUnitedStates.compareString("abc", "abd", false)).isNegative();
        assertThat(discoveredEnglish.compareString("abc", "ABC", true)).isZero();
    }
}
