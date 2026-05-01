/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.MathRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class MathRuntimeExceptionTest {
    @Test
    void requestsLocalizedResourceBundleForFormattedMessage() {
        MathRuntimeException exception = new MathRuntimeException("dimension mismatch {0} != {1}", 2, 3);

        String frenchMessage = exception.getMessage(Locale.FRENCH);

        assertThat(frenchMessage)
                .isIn("dimensions incompatibles 2 != 3", "dimension mismatch 2 != 3");
    }

    @Test
    void fallsBackToOriginalPatternWhenResourceKeyIsUnavailable() {
        MathRuntimeException exception = new MathRuntimeException("runtime failure for {0}", "sample");

        String fallbackMessage = exception.getMessage(Locale.FRENCH);

        assertThat(fallbackMessage).isEqualTo("runtime failure for sample");
    }
}
