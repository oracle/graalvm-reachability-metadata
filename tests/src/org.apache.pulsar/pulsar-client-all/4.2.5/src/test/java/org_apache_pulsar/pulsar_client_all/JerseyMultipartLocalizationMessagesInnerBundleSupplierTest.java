/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.media.multipart.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;

public class JerseyMultipartLocalizationMessagesInnerBundleSupplierTest {
    @Test
    void loadsMultipartLocalizationBundle() {
        assertThat(LocalizationMessages.ERROR_READING_ENTITY("payload")).contains("payload");
    }
}
