/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.client.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;

public class JerseyClientLocalizationMessagesInnerBundleSupplierTest {
    @Test
    void loadsClientLocalizationBundle() {
        assertThat(LocalizationMessages.CHUNKED_INPUT_STREAM_CLOSING_ERROR()).isNotBlank();
    }
}
