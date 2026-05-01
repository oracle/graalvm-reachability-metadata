/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.neko_htmlunit;

import java.util.MissingResourceException;

import net.sourceforge.htmlunit.xerces.util.SAXMessageFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class SAXMessageFormatterTest {

    @Test
    void formatsSaxMessagesFromResourceBundle() {
        final String featureName = "http://example.com/sax/feature";

        final String message = SAXMessageFormatter.formatMessage(
                "feature-not-recognized",
                new Object[] {featureName}
        );

        assertThat(message)
                .contains(featureName)
                .contains("not recognized");
    }

    @Test
    void reportsMissingSaxMessageKeys() {
        final String missingKey = "missing-sax-message-key";

        assertThatExceptionOfType(MissingResourceException.class)
                .isThrownBy(() -> SAXMessageFormatter.formatMessage(missingKey, null))
                .satisfies(exception -> assertThat(exception.getKey()).isEqualTo(missingKey));
    }
}
