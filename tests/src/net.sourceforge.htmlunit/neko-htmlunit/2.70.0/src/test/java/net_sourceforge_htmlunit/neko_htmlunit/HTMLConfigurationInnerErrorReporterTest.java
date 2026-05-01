/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.neko_htmlunit;

import net.sourceforge.htmlunit.cyberneko.HTMLConfiguration;
import net.sourceforge.htmlunit.cyberneko.HTMLErrorReporter;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLConfigurationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HTMLConfigurationInnerErrorReporterTest {

    private static final String ERROR_REPORTER_PROPERTY =
            "http://cyberneko.org/html/properties/error-reporter";

    @Test
    void formatsHtmlErrorMessagesFromResourceBundle() throws XMLConfigurationException {
        final HTMLConfiguration configuration = new HTMLConfiguration();
        final Object errorReporter = configuration.getProperty(ERROR_REPORTER_PROPERTY);

        assertThat(errorReporter).isInstanceOf(HTMLErrorReporter.class);

        final String message = ((HTMLErrorReporter) errorReporter).formatMessage(
                "HTML1005",
                new Object[] {"bogusEntity"}
        );

        assertThat(message).isEqualTo("Invalid character entity \"bogusEntity\".");
    }
}
