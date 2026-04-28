/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.value.CompareMode;
import org.h2.value.CompareModeIcu4J;
import org.junit.jupiter.api.Test;

import java.text.Collator;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareModeIcu4JTest {
    @Test
    void createsIcuCollatorForLanguageLocale() {
        CompareMode compareMode = CompareMode.getInstance(CompareMode.ICU4J + "en", Collator.TERTIARY);

        assertThat(compareMode.getName()).isEqualTo("en");
        assertThat(compareMode).isInstanceOf(CompareModeIcu4J.class);
        assertThat(compareMode.compareString("abc", "abc", false)).isZero();
        assertThat(compareMode.compareString("abc", "abd", false)).isNegative();
        assertThat(compareMode.equalsChars("A", 0, "a", 0, true)).isTrue();
    }

    @Test
    void createsIcuCollatorForLanguageCountryLocale() {
        CompareMode compareMode = CompareMode.getInstance(CompareMode.ICU4J + "en_US", Collator.TERTIARY);

        assertThat(compareMode.getName()).isEqualTo("en_US");
        assertThat(compareMode).isInstanceOf(CompareModeIcu4J.class);
        assertThat(compareMode.compareString("coffee", "coffee", false)).isZero();
        assertThat(compareMode.compareString("coffee", "tea", false)).isNegative();
        assertThat(compareMode.equalsChars("B", 0, "b", 0, true)).isTrue();
    }

    @Test
    void createsIcuCollatorFromAvailableLocaleDisplayName() {
        CompareMode compareMode = CompareMode.getInstance(CompareMode.ICU4J + "ENGLISH_UNITED_STATES", Collator.PRIMARY);

        assertThat(compareMode.getName()).isEqualTo("ENGLISH_UNITED_STATES");
        assertThat(compareMode).isInstanceOf(CompareModeIcu4J.class);
        assertThat(compareMode.compareString("resume", "résumé", false)).isZero();
        assertThat(compareMode.compareString("alpha", "omega", false)).isNegative();
    }
}
