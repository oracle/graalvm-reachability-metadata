/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.weaver.WeaverMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WeaverMessagesTest {
    @Test
    void formatsMessageFromResourceBundle() {
        String message = WeaverMessages.format(WeaverMessages.EXACT_TYPE_PATTERN_REQD);

        assertThat(message).isNotBlank().isNotEqualTo(WeaverMessages.EXACT_TYPE_PATTERN_REQD);
    }
}
