/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.message.filtering.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;

public class JerseyMessageFilteringLocalizationMessagesInnerBundleSupplierTest {
    @Test
    void loadsEntityFilteringLocalizationBundle() {
        assertThat(LocalizationMessages.MERGING_FILTERING_SCOPES()).isNotBlank();
    }
}
