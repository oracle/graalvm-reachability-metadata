/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_apache_commons_compress;

import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.compress.harmony.archive.internal.nls.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {

    private static final String MISSING_MESSAGES_BUNDLE =
            "com_clickhouse.org_apache_commons_compress.MessagesTestMissingBundle";

    @Test
    void setLocaleReturnsNullWhenBundleCannotBeLoaded() {
        ResourceBundle bundle = Messages.setLocale(Locale.ROOT, MISSING_MESSAGES_BUNDLE);

        assertThat(bundle).isNull();
    }
}
