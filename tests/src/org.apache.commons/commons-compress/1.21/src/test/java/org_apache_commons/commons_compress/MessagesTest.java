/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.compress.harmony.archive.internal.nls.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {

    @Test
    void setLocaleReturnsNullWhenBundleIsMissing() {
        String resourceName = getClass().getPackageName() + ".MissingMessages" + System.nanoTime();

        ResourceBundle bundle = Messages.setLocale(Locale.ROOT, resourceName);

        assertThat(bundle).isNull();
    }

    @Test
    void getStringFormatsArgumentsWhenMessageKeyIsUnavailable() {
        String message = Messages.getString("forge2-messages-test {0} {1}", new Object[] {"alpha", null});

        assertThat(message).isEqualTo("forge2-messages-test alpha <null>");
    }
}
