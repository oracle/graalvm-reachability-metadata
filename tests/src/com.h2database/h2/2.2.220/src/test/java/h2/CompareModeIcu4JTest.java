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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareModeIcu4JTest {
    @Test
    void createsCollatorForTwoLetterLanguageName() {
        CompareMode compareMode = CompareMode.getInstance(CompareMode.ICU4J + "en", Collator.PRIMARY);

        assertThat(compareMode.compareString("alpha", "alpha", false)).isZero();
        assertThat(compareMode.compareString("alpha", "beta", false)).isNegative();
    }

    @Test
    void createsCollatorForLanguageAndCountryName() {
        CompareMode compareMode = CompareMode.getInstance(CompareMode.ICU4J + "en_US", Collator.SECONDARY);

        assertThat(compareMode.compareString("database", "database", false)).isZero();
        assertThat(compareMode.compareString("database", "table", false)).isNegative();
    }

    @Test
    void createsCollatorFromAvailableLocaleDisplayName() {
        String localeName = CompareMode.getName(Locale.US);
        CompareMode compareMode = CompareMode.getInstance(CompareMode.ICU4J + localeName, Collator.TERTIARY);

        assertThat(compareMode.compareString("row", "row", false)).isZero();
        assertThat(compareMode.equalsChars("A", 0, "a", 0, true)).isTrue();
    }
}
