/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.value.CompareMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.text.Collator;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareModeIcu4JTest {
    private static final String SERIALIZER_PROPERTY = "h2.javaObjectSerializer";

    @BeforeAll
    static void configureJavaObjectSerializerProperty() {
        System.setProperty(SERIALIZER_PROPERTY, JdbcUtilsTest.TestJavaObjectSerializer.class.getName());
    }

    @Test
    void usesIcu4jCollatorForLanguageName() {
        CompareMode mode = CompareMode.getInstance(CompareMode.ICU4J + "en", Collator.TERTIARY);

        assertThat(mode.getName()).isEqualTo("en");
        assertThat(mode.compareString("abc", "abd", false)).isNegative();
        assertThat(mode.compareString("H2", "h2", true)).isZero();
        assertThat(mode.equalsChars("A", 0, "a", 0, true)).isTrue();
    }

    @Test
    void usesIcu4jCollatorForLanguageAndCountryName() {
        CompareMode mode = CompareMode.getInstance(CompareMode.ICU4J + "en_US", Collator.SECONDARY);

        assertThat(mode.getName()).isEqualTo("en_US");
        assertThat(mode.compareString("DATABASE", "database", false)).isZero();
    }

    @Test
    void resolvesHyphenatedIcu4jLocaleFromAvailableLocales() {
        CompareMode mode = CompareMode.getInstance(CompareMode.ICU4J + "en-US", Collator.PRIMARY);

        assertThat(mode.getName()).isEqualTo("en-US");
        assertThat(mode.compareString("resume", "résumé", false)).isZero();
    }
}
