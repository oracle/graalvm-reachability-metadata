/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_cal10n.cal10n_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import ch.qos.cal10n.util.CAL10NResourceBundle;
import ch.qos.cal10n.util.CAL10NResourceBundleFinder;
import org.junit.jupiter.api.Test;

public class CAL10NResourceBundleFinderTest {
    @Test
    public void loadsLanguageAndCountryResourceBundleWithParentFallback() {
        ClassLoader classLoader = CAL10NResourceBundleFinderTest.class.getClassLoader();

        CAL10NResourceBundle bundle = CAL10NResourceBundleFinder.getBundle(
                classLoader,
                "cal10n.messages",
                Locale.US,
                "UTF-8");

        assertThat(bundle).isNotNull();
        assertThat(bundle.getString("GREETING")).isEqualTo("Howdy {0}");
        assertThat(bundle.getString("FAREWELL")).isEqualTo("Goodbye");
    }
}
