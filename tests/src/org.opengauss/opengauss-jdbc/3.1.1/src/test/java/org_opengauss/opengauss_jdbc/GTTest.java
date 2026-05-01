/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.util.GT;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.util.GT}.
 */
public class GTTest {

    @Test
    void translateLoadsMessageBundleAndFormatsArguments() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            String translatedMessage = GT.tr("Where: {0}", "SELECT 1");

            assertThat(translatedMessage).contains("SELECT 1");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
