/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import org.junit.jupiter.api.Test;

public class LocalizedMessageTest {
    private static final String CHECKS_BUNDLE = "com.puppycrawl.tools.checkstyle.checks.messages";

    @Test
    void formatsMessageFromCheckstyleResourceBundle() {
        Locale originalLocale = Locale.getDefault();
        LocalizedMessage.clearCache();
        LocalizedMessage.setLocale(Locale.ENGLISH);
        try {
            LocalizedMessage message = new LocalizedMessage(
                10,
                2,
                CHECKS_BUNDLE,
                "final.parameter",
                new Object[] {"argument"},
                SeverityLevel.WARNING,
                "final-parameters",
                LocalizedMessage.class,
                null);

            assertThat(message.getMessage())
                .isIn("Parameter argument should be final.", "final.parameter");
        }
        finally {
            LocalizedMessage.setLocale(originalLocale);
            LocalizedMessage.clearCache();
        }
    }
}
