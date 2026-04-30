/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_api.api_i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.directory.api.i18n.I18n;
import org.junit.jupiter.api.Test;

public class I18nTest {
    @Test
    void formatsKnownErrorMessagesFromTheResourceBundle() {
        String message = I18n.err(I18n.ERR_01200_BAD_TRANSITION_FROM_STATE, "START", "0x30");

        assertThat(message)
                .startsWith("ERR_01200_BAD_TRANSITION_FROM_STATE ")
                .contains("Bad transition from state START, tag 0x30");
    }

    @Test
    void formatsMessagePatternsAfterLoadingMessageResourceBundle() {
        String message = I18n.msg("Directory API {0} message", "i18n");

        assertThat(message).isEqualTo("Directory API i18n message");
    }
}
