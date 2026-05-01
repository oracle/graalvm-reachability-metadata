/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.MathException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class MathExceptionTest {
    @Test
    void requestsFrenchResourceBundleForFormattedMessage() {
        MathException exception = new MathException("dimension mismatch {0} != {1}", 2, 3);

        String frenchMessage = exception.getMessage(Locale.FRENCH);

        assertThat(frenchMessage)
                .isIn("dimensions incompatibles 2 != 3", "dimension mismatch 2 != 3");
    }

    @Test
    void fallsBackToOriginalPatternWhenTranslationIsUnavailable() {
        MathException exception = new MathException("custom failure for {0}", "sample");

        String fallbackMessage = exception.getMessage(Locale.FRENCH);

        assertThat(fallbackMessage).isEqualTo("custom failure for sample");
    }
}
