/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.log.LogUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogUtilsTest {
    private static final String BUNDLE_NAME = "com_mchange.mchange_commons_java.LogUtilsTestMessages";

    @Test
    void formatMessageLoadsMessagePatternFromResourceBundle() {
        String formattedMessage = LogUtils.formatMessage(
                BUNDLE_NAME,
                "message.template",
                new Object[]{"world"});

        assertThat(formattedMessage).isEqualTo("Resource bundle says hello to world");
    }
}
