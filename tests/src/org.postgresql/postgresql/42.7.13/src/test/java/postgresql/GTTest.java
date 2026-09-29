/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import org.junit.jupiter.api.Test;
import org.postgresql.util.GT;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.util.GT}.
 */
public class GTTest {

    private static final String TRANSLATED_MESSAGE = translateWithGermanDisplayLocale();

    private static String translateWithGermanDisplayLocale() {
        Locale previousDisplayLocale = Locale.getDefault(Locale.Category.DISPLAY);
        try {
            Locale.setDefault(Locale.Category.DISPLAY, Locale.GERMAN);
            return GT.tr("Where: {0}", "SELECT 1");
        } finally {
            Locale.setDefault(Locale.Category.DISPLAY, previousDisplayLocale);
        }
    }

    @Test
    void translateLoadsResourceBundleAndFormatsMessage() {
        assertThat(TRANSLATED_MESSAGE).contains("SELECT 1");
    }
}
