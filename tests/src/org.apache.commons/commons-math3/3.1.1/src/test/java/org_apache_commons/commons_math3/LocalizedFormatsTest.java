/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.junit.jupiter.api.Test;

public class LocalizedFormatsTest {
    @Test
    void resolvesFrenchMessageFromResourceBundle() {
        String localizedMessage = LocalizedFormats.DIMENSIONS_MISMATCH_2x2.getLocalizedString(Locale.FRENCH);

        assertThat(localizedMessage).isEqualTo("{0}x{1} à la place de {2}x{3}");
        assertThat(localizedMessage).isNotEqualTo(LocalizedFormats.DIMENSIONS_MISMATCH_2x2.getSourceString());
    }
}
