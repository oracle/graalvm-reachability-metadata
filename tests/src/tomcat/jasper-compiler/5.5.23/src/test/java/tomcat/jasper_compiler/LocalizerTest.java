/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jasper.compiler.Localizer;
import org.junit.jupiter.api.Test;

public class LocalizerTest {
    @Test
    void getMessageLoadsJasperResourceBundleAndFallsBackForUnknownKeys() {
        final String missingMessageKey = "tomcat.jasper_compiler.LocalizerTest.missing";

        final String message = Localizer.getMessage(missingMessageKey);

        assertThat(message).isEqualTo(missingMessageKey);
    }

    @Test
    void getMessageFormatsArgumentsFromLocalizedPattern() {
        final String scratchDirectory = "/tmp/jasper-scratch";

        final String message = Localizer.getMessage("jsp.error.bad.scratch.dir", scratchDirectory);

        assertThat(message).contains(scratchDirectory);
    }
}
