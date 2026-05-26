/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.util.GT;

import static org.assertj.core.api.Assertions.assertThat;

public class GTTest {
    @Test
    void loadsTranslationBundleThroughPublicMessageTranslator() {
        String message = GT.tr("Where: {0}", "SELECT 1");

        assertThat(message).contains("SELECT 1");
    }

    @Test
    void formatsMessageFormatArgumentsThroughPublicApi() {
        String message = GT.tr("openGauss connection {0}", "ready");

        assertThat(message).isEqualTo("openGauss connection ready");
    }
}
