/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.directory.server.i18n.I18n;
import org.junit.jupiter.api.Test;

public class I18nTest {
    @Test
    public void loadsResourceBundlesForErrorAndMessageFormatting() {
        String oid = "1.2.840.113556.1.4.803";

        assertThat(I18n.ERR_1.getErrorCode()).isEqualTo("ERR_1");

        String translatedError = I18n.err(I18n.ERR_1, oid);
        assertThat(translatedError).startsWith("ERR_1 ");
        assertThat(translatedError).contains(oid);
        assertThat(translatedError).isNotEqualTo("ERR_1 (" + oid + ")");

        assertThat(I18n.msg("Server {0} started", "apacheds")).isEqualTo("Server apacheds started");
    }
}
