/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_jxc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.tools.jxc.ap.Options;
import com.sun.tools.xjc.BadCommandLineException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ApMessagesTest {
    @Test
    void unrecognizedOptionLoadsAnnotationProcessorMessageBundle() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            Options options = new Options();

            assertThatThrownBy(() -> options.parseArguments(new String[] {"-not-a-jxc-option"}))
                    .isInstanceOf(BadCommandLineException.class)
                    .hasMessageContaining("Unrecognized option -not-a-jxc-option is not valid.");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
