/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.cyberneko.html.HTMLConfiguration;
import org.cyberneko.html.HTMLErrorReporter;
import org.junit.jupiter.api.Test;

public class HTMLConfigurationInnerErrorReporterTest {
    private static final String ERROR_REPORTER = "http://cyberneko.org/html/properties/error-reporter";

    @Test
    void formatMessageLoadsLocalizedResourceBundle() throws Exception {
        HTMLConfiguration configuration = new HTMLConfiguration();
        configuration.setLocale(Locale.US);
        HTMLErrorReporter reporter = (HTMLErrorReporter) configuration.getProperty(ERROR_REPORTER);

        String message = reporter.formatMessage("HTML2001", new Object[] { "section" });

        assertEquals("Element <section> not closed properly.", message);
    }
}
