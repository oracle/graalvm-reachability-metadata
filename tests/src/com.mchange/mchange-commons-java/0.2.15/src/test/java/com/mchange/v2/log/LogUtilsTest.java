/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogUtilsTest {
    private static final String BUNDLE_NAME = "com.mchange.v2.log.LogUtilsTestMessages";
    private static final String MESSAGE_KEY = "translated.message";

    @Test
    void formatMessageLoadsMessagesFromResourceBundles() {
        String formatted = LogUtils.formatMessage(BUNDLE_NAME, MESSAGE_KEY, new Object[] {"value"});

        assertThat(formatted).isEqualTo("Translated LogUtils message value");
    }
}
